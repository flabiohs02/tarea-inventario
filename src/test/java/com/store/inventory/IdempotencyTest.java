package com.store.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.store.inventory.api.InventoryService;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.Reservation;
import com.store.inventory.support.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The app resends an order automatically when the connection is slow. */
class IdempotencyTest {

    private final MutableClock clock = MutableClock.startingAt("2026-01-01T10:00:00Z");
    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = Inventory.create(clock, (sku, available) -> { });
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.registerProduct("SKU-2", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);
        service.addStock("SKU-2", 10);
    }

    @Test
    void resentOrderDoesNotReserveTwice() {
        Reservation first = service.reserve("ORDER-1", "SKU-1", 3);
        clock.advance(Duration.ofMinutes(1));
        Reservation retry = service.reserve("ORDER-1", "SKU-1", 3);

        assertEquals(first, retry);
        assertEquals(7, service.available("SKU-1"));
    }

    @Test
    void resentOrderAfterPaymentReturnsTheSameReservation() {
        Reservation first = service.reserve("ORDER-1", "SKU-1", 3);
        service.confirm("ORDER-1");

        assertEquals(first, service.reserve("ORDER-1", "SKU-1", 3));
        assertEquals(7, service.available("SKU-1"));
    }

    @Test
    void sameOrderIdWithDifferentDataIsRejected() {
        service.reserve("ORDER-1", "SKU-1", 3);

        assertThrows(IllegalStateException.class, () -> service.reserve("ORDER-1", "SKU-1", 4));
        assertThrows(IllegalStateException.class, () -> service.reserve("ORDER-1", "SKU-2", 3));
        assertEquals(7, service.available("SKU-1"));
        assertEquals(10, service.available("SKU-2"));
    }

    @Test
    void retryIsAcceptedEvenIfStockRanOutMeanwhile() {
        service.reserve("ORDER-1", "SKU-1", 3);
        service.reserve("ORDER-2", "SKU-1", 7);

        assertEquals(3, service.reserve("ORDER-1", "SKU-1", 3).quantity());
    }

    @Test
    void registeringTheSameProductTwiceKeepsItsStock() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);

        assertEquals(10, service.available("SKU-1"));
        assertThrows(IllegalStateException.class,
                () -> service.registerProduct("SKU-1", ProductCategory.FLASH_SALE));
    }
}
