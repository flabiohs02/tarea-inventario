package com.store.inventory.infrastructure;

import com.store.inventory.application.port.ReservationRepository;
import com.store.inventory.domain.StockReservation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps reservations in memory. {@link #findActiveBySku} scans all reservations, which is fine for
 * the current scope; a database would use an index on (sku, status, expires_at).
 */
public final class InMemoryReservationRepository implements ReservationRepository {

    private final Map<String, StockReservation> byOrderId = new ConcurrentHashMap<>();

    @Override
    public Optional<StockReservation> findByOrderId(String orderId) {
        return Optional.ofNullable(byOrderId.get(orderId));
    }

    @Override
    public List<StockReservation> findActiveBySku(String sku, Instant now) {
        return byOrderId.values().stream()
                .filter(r -> r.sku().equals(sku) && r.isActiveAt(now))
                .toList();
    }

    @Override
    public void save(StockReservation reservation) {
        byOrderId.put(reservation.orderId(), reservation);
    }
}
