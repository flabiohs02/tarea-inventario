package com.store.inventory.application.port;

import com.store.inventory.domain.StockReservation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Storage of reservations, one per order. Today in memory; tomorrow a database. */
public interface ReservationRepository {

    Optional<StockReservation> findByOrderId(String orderId);

    /** Reservations of {@code sku} that are still holding units at {@code now}. */
    List<StockReservation> findActiveBySku(String sku, Instant now);

    /** Inserts or replaces the reservation of its order. */
    void save(StockReservation reservation);
}
