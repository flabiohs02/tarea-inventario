package com.store.inventory.domain;

/**
 * Stored status of a reservation. "Expired" is not stored: an {@code ACTIVE} reservation whose
 * deadline has passed is considered expired (see {@link StockReservation#isActiveAt}).
 */
public enum ReservationStatus {
    ACTIVE,
    CONFIRMED
}
