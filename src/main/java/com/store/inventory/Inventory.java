package com.store.inventory;

import com.store.inventory.api.InventoryService;
import com.store.inventory.api.StockAlertListener;
import com.store.inventory.application.InventoryReservationService;
import com.store.inventory.application.LowStockNotifier;
import com.store.inventory.domain.CategoryPolicies;
import com.store.inventory.domain.LowStockRule;
import com.store.inventory.infrastructure.InMemoryProductRepository;
import com.store.inventory.infrastructure.InMemoryReservationRepository;
import java.time.Clock;
import java.util.Objects;

/**
 * Entry point used by our automated tests. Keep this signature exactly as it is,
 * and build your implementation here.
 *
 * <p>This is the composition root: the only place that knows which concrete implementations are
 * wired together. Moving to a database means swapping the repositories here.
 */
public final class Inventory {

    private Inventory() {
    }

    public static InventoryService create(Clock clock, StockAlertListener alertListener) {
        return create(clock, alertListener, CategoryPolicies.defaults(), LowStockRule.defaultRule());
    }

    /**
     * Same service, with business rules supplied from outside (for example from
     * {@code application.properties}).
     */
    public static InventoryService create(
            Clock clock,
            StockAlertListener alertListener,
            CategoryPolicies policies,
            LowStockRule lowStockRule) {
        Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(alertListener, "alertListener must not be null");
        return new InventoryReservationService(
                new InMemoryProductRepository(),
                new InMemoryReservationRepository(),
                policies,
                lowStockRule,
                new LowStockNotifier(alertListener),
                clock);
    }
}
