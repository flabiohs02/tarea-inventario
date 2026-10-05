package com.store.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.store.inventory.api.InventoryService;
import com.store.inventory.api.OrderLimitExceededException;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.Reservation;
import com.store.inventory.support.MutableClock;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class CategoryRulesTest {

    private final MutableClock clock = MutableClock.startingAt("2026-01-01T10:00:00Z");
    private final InventoryService service = Inventory.create(clock, (sku, available) -> { });

    @ParameterizedTest
    @CsvSource({"STANDARD, PT15M", "PRE_ORDER, PT24H", "FLASH_SALE, PT5M"})
    void paymentWindowDependsOnCategory(ProductCategory category, Duration window) {
        service.registerProduct("SKU", category);
        service.addStock("SKU", 10);

        Reservation reservation = service.reserve("ORDER-1", "SKU", 1);
        assertEquals(clock.instant().plus(window), reservation.expiresAt());

        clock.advance(window.minusSeconds(1));
        assertEquals(9, service.available("SKU"));
        clock.advance(Duration.ofSeconds(1));
        assertEquals(10, service.available("SKU"));
    }

    @Test
    void flashSaleAllowsUpToTwoUnitsPerOrder() {
        service.registerProduct("FLASH", ProductCategory.FLASH_SALE);
        service.addStock("FLASH", 100);

        service.reserve("ORDER-1", "FLASH", 2);
        assertThrows(OrderLimitExceededException.class, () -> service.reserve("ORDER-2", "FLASH", 3));
        assertEquals(98, service.available("FLASH"));
    }

    @Test
    void orderLimitIsCheckedEvenWithoutEnoughStock() {
        service.registerProduct("FLASH", ProductCategory.FLASH_SALE);
        service.addStock("FLASH", 1);

        assertThrows(OrderLimitExceededException.class, () -> service.reserve("ORDER-1", "FLASH", 3));
    }

    @ParameterizedTest
    @CsvSource({"STANDARD", "PRE_ORDER"})
    void otherCategoriesHaveNoOrderLimit(ProductCategory category) {
        service.registerProduct("SKU", category);
        service.addStock("SKU", 1_000);

        service.reserve("ORDER-1", "SKU", 1_000);

        assertEquals(0, service.available("SKU"));
    }
}
