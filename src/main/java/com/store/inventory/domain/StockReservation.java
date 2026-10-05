package com.store.inventory.domain;

import com.store.inventory.api.Reservation;
import java.time.Instant;
import java.util.Objects;

/**
 * Units of one product held for one order while the customer pays.
 *
 * <p>Lifecycle: {@code ACTIVE} → {@code CONFIRMED} when paid, or expired when {@code now >= expiresAt}
 * without being confirmed. Expired reservations simply stop counting against available stock.
 */
public final class StockReservation {

    private final String orderId;
    private final String sku;
    private final int quantity;
    private final Instant expiresAt;
    private ReservationStatus status;

    public StockReservation(String orderId, String sku, int quantity, Instant expiresAt) {
        this.orderId = Product.requireText(orderId, "orderId");
        this.sku = Product.requireText(sku, "sku");
        Product.requirePositive(quantity);
        this.quantity = quantity;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.status = ReservationStatus.ACTIVE;
    }

    public String orderId() {
        return orderId;
    }

    public String sku() {
        return sku;
    }

    public int quantity() {
        return quantity;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public ReservationStatus status() {
        return status;
    }

    public boolean isConfirmed() {
        return status == ReservationStatus.CONFIRMED;
    }

    /** {@code true} while the units are held: not confirmed and the payment deadline has not passed. */
    public boolean isActiveAt(Instant now) {
        return status == ReservationStatus.ACTIVE && now.isBefore(expiresAt);
    }

    /** {@code true} if the order still counts: it is either held or already sold. */
    public boolean isLiveAt(Instant now) {
        return isConfirmed() || isActiveAt(now);
    }

    public boolean isExpiredAt(Instant now) {
        return !isLiveAt(now);
    }

    /** Whether a (possibly retried) request asks for exactly this reservation. */
    public boolean matches(String sku, int quantity) {
        return this.sku.equals(sku) && this.quantity == quantity;
    }

    public void confirm(Instant now) {
        if (!isActiveAt(now)) {
            throw new IllegalStateException("Order " + orderId + " has no active reservation");
        }
        status = ReservationStatus.CONFIRMED;
    }

    public Reservation toApi() {
        return new Reservation(orderId, sku, quantity, expiresAt);
    }
}
