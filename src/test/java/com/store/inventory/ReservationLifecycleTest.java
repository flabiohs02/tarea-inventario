package com.store.inventory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.store.inventory.api.InventoryService;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.Reservation;
import com.store.inventory.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReservationLifecycleTest {

    private final MutableClock clock = MutableClock.startingAt("2026-01-01T10:00:00Z");
    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = Inventory.create(clock, (sku, available) -> { });
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);
    }

    @Test
    void reservationReturnsOrderDataAndDeadline() {
        Reservation reservation = service.reserve("ORDER-1", "SKU-1", 3);

        assertEquals(new Reservation("ORDER-1", "SKU-1", 3, Instant.parse("2026-01-01T10:15:00Z")), reservation);
    }

    @Test
    void unitsStayReservedUntilJustBeforeTheDeadline() {
        service.reserve("ORDER-1", "SKU-1", 3);
        clock.advance(Duration.ofMinutes(15).minusNanos(1));

        assertEquals(7, service.available("SKU-1"));
    }

    @Test
    void unpaidReservationIsReleasedAtTheDeadline() {
        service.reserve("ORDER-1", "SKU-1", 3);
        clock.advance(Duration.ofMinutes(15));

        assertEquals(10, service.available("SKU-1"));
    }

    @Test
    void releasedUnitsCanBeBoughtByOtherCustomers() {
        service.reserve("ORDER-1", "SKU-1", 10);
        clock.advance(Duration.ofMinutes(15));

        service.reserve("ORDER-2", "SKU-1", 10);

        assertEquals(0, service.available("SKU-1"));
    }

    @Test
    void confirmedUnitsNeverReturnToStock() {
        service.reserve("ORDER-1", "SKU-1", 4);
        service.confirm("ORDER-1");
        clock.advance(Duration.ofDays(30));

        assertEquals(6, service.available("SKU-1"));
    }

    @Test
    void cannotConfirmAnExpiredReservation() {
        service.reserve("ORDER-1", "SKU-1", 4);
        clock.advance(Duration.ofMinutes(15));

        assertThrows(IllegalStateException.class, () -> service.confirm("ORDER-1"));
        assertEquals(10, service.available("SKU-1"));
    }

    @Test
    void cannotConfirmAnUnknownOrder() {
        assertThrows(IllegalStateException.class, () -> service.confirm("NOPE"));
    }

    @Test
    void confirmingTwiceIsHarmless() {
        service.reserve("ORDER-1", "SKU-1", 4);
        service.confirm("ORDER-1");

        assertDoesNotThrow(() -> service.confirm("ORDER-1"));
        assertEquals(6, service.available("SKU-1"));
    }

    @Test
    void expiredOrderCanReserveAgain() {
        service.reserve("ORDER-1", "SKU-1", 4);
        clock.advance(Duration.ofMinutes(20));

        Reservation again = service.reserve("ORDER-1", "SKU-1", 4);

        assertEquals(Instant.parse("2026-01-01T10:35:00Z"), again.expiresAt());
        assertEquals(6, service.available("SKU-1"));
    }

    @Test
    void restockAddsToAvailableUnits() {
        service.reserve("ORDER-1", "SKU-1", 4);
        service.addStock("SKU-1", 5);

        assertEquals(11, service.available("SKU-1"));
    }
}
