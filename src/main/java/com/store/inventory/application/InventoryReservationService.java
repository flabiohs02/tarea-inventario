package com.store.inventory.application;

import com.store.inventory.api.InsufficientStockException;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.Reservation;
import com.store.inventory.application.port.ProductRepository;
import com.store.inventory.application.port.ReservationRepository;
import com.store.inventory.domain.CategoryPolicies;
import com.store.inventory.domain.CategoryPolicy;
import com.store.inventory.domain.LowStockRule;
import com.store.inventory.domain.LowStockRule.LowStockAlert;
import com.store.inventory.domain.Product;
import com.store.inventory.domain.StockReservation;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Use cases of the inventory: register products, restock, reserve, confirm and query availability.
 *
 * <p>This class only orchestrates. Business rules live in the domain ({@link CategoryPolicy},
 * {@link LowStockRule}, {@link StockReservation}), and storage sits behind the repository ports.
 *
 * <p><b>Concurrency:</b> every read-check-write on a product's stock happens under that product's
 * lock, so two customers can never reserve the same unit. {@code reserve} and {@code confirm} first
 * take the order's lock (always order → product, so they cannot deadlock), which makes the app's
 * automatic retries safe. Alerts are sent after releasing the locks.
 */
public final class InventoryReservationService implements InventoryService {

    private final ProductRepository products;
    private final ReservationRepository reservations;
    private final CategoryPolicies policies;
    private final LowStockRule lowStockRule;
    private final LowStockNotifier notifier;
    private final Clock clock;

    private final KeyedLocks productLocks = new KeyedLocks();
    private final KeyedLocks orderLocks = new KeyedLocks();

    public InventoryReservationService(
            ProductRepository products,
            ReservationRepository reservations,
            CategoryPolicies policies,
            LowStockRule lowStockRule,
            LowStockNotifier notifier,
            Clock clock) {
        this.products = Objects.requireNonNull(products);
        this.reservations = Objects.requireNonNull(reservations);
        this.policies = Objects.requireNonNull(policies);
        this.lowStockRule = Objects.requireNonNull(lowStockRule);
        this.notifier = Objects.requireNonNull(notifier);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void registerProduct(String sku, ProductCategory category) {
        Product candidate = new Product(sku, category);
        productLocks.withLock(sku, () -> {
            Optional<Product> existing = products.findBySku(sku);
            if (existing.isEmpty()) {
                products.save(candidate);
            } else if (existing.get().category() != category) {
                throw new IllegalStateException(
                        "Product " + sku + " is already registered as " + existing.get().category());
            }
            return null;
        });
    }

    @Override
    public void addStock(String sku, int quantity) {
        requirePositive(quantity);
        Optional<LowStockAlert> alert = productLocks.withLock(requireSku(sku), () -> {
            Product product = products.findBySku(sku)
                    .orElseThrow(() -> new IllegalArgumentException("Product " + sku + " is not registered"));
            product.addStock(quantity);
            products.save(product);
            return lowStockRule.afterRestock(product, availableUnits(product, clock.instant()));
        });
        notifier.send(alert);
    }

    @Override
    public Reservation reserve(String orderId, String sku, int quantity) {
        requirePositive(quantity);
        requireText(orderId, "orderId");
        requireSku(sku);

        ReserveOutcome outcome = orderLocks.withLock(orderId, () -> {
            Instant now = clock.instant();
            Optional<StockReservation> previous = reservations.findByOrderId(orderId);
            if (previous.isPresent() && previous.get().isLiveAt(now)) {
                return retryOf(previous.get(), sku, quantity);
            }
            // No previous reservation, or it expired: the order may reserve again.
            return productLocks.withLock(sku, () -> reserveUnits(orderId, sku, quantity, now));
        });

        notifier.send(outcome.alert());
        return outcome.reservation();
    }

    @Override
    public void confirm(String orderId) {
        requireText(orderId, "orderId");
        orderLocks.withLock(orderId, () -> {
            StockReservation reservation = reservations.findByOrderId(orderId)
                    .orElseThrow(() -> new IllegalStateException("Order " + orderId + " has no active reservation"));
            if (reservation.isConfirmed()) {
                return null; // Payment notification retried: already sold, nothing to do.
            }
            return productLocks.withLock(reservation.sku(), () -> {
                reservation.confirm(clock.instant());
                Product product = products.findBySku(reservation.sku()).orElseThrow();
                product.removeSoldUnits(reservation.quantity());
                reservations.save(reservation);
                products.save(product);
                return null;
            });
        });
    }

    @Override
    public int available(String sku) {
        if (sku == null) {
            return 0;
        }
        return productLocks.withLock(sku, () -> products.findBySku(sku)
                .map(product -> availableUnits(product, clock.instant()))
                .orElse(0));
    }

    private ReserveOutcome reserveUnits(String orderId, String sku, int quantity, Instant now) {
        Product product = products.findBySku(sku)
                .orElseThrow(() -> new InsufficientStockException(sku, quantity, 0));
        CategoryPolicy policy = policies.policyFor(product.category());
        policy.checkOrderLimit(sku, quantity);

        int available = availableUnits(product, now);
        if (quantity > available) {
            throw new InsufficientStockException(sku, quantity, available);
        }

        StockReservation reservation =
                new StockReservation(orderId, sku, quantity, policy.expirationFrom(now));
        reservations.save(reservation);
        Optional<LowStockAlert> alert = lowStockRule.afterReservation(product, available - quantity);
        products.save(product);
        return new ReserveOutcome(reservation.toApi(), alert);
    }

    private static ReserveOutcome retryOf(StockReservation previous, String sku, int quantity) {
        if (!previous.matches(sku, quantity)) {
            throw new IllegalStateException("Order " + previous.orderId() + " already reserved "
                    + previous.quantity() + " units of " + previous.sku());
        }
        return new ReserveOutcome(previous.toApi(), Optional.empty());
    }

    /** Stock on hand minus units held by reservations that have not expired yet. */
    private int availableUnits(Product product, Instant now) {
        int held = reservations.findActiveBySku(product.sku(), now).stream()
                .mapToInt(StockReservation::quantity)
                .sum();
        return product.stockOnHand() - held;
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, was " + quantity);
        }
    }

    private static String requireSku(String sku) {
        return requireText(sku, "sku");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private record ReserveOutcome(Reservation reservation, Optional<LowStockAlert> alert) {
    }
}
