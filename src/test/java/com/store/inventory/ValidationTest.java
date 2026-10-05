package com.store.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.store.inventory.api.InsufficientStockException;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.ProductCategory;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidationTest {

    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = Inventory.create(Clock.systemUTC(), (sku, available) -> { });
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveQuantities(int quantity) {
        assertThrows(IllegalArgumentException.class, () -> service.addStock("SKU-1", quantity));
        assertThrows(IllegalArgumentException.class, () -> service.reserve("ORDER-1", "SKU-1", quantity));
    }

    @Test
    void quantityIsValidatedBeforeTheProductExists() {
        assertThrows(IllegalArgumentException.class, () -> service.reserve("ORDER-1", "UNKNOWN", 0));
    }

    @Test
    void cannotAddStockToUnregisteredProduct() {
        assertThrows(IllegalArgumentException.class, () -> service.addStock("UNKNOWN", 5));
    }

    @Test
    void unknownProductHasNoUnits() {
        assertEquals(0, service.available("UNKNOWN"));
        assertThrows(InsufficientStockException.class, () -> service.reserve("ORDER-1", "UNKNOWN", 1));
    }

    @Test
    void registeredProductStartsWithoutStock() {
        assertEquals(0, service.available("SKU-1"));
        assertThrows(InsufficientStockException.class, () -> service.reserve("ORDER-1", "SKU-1", 1));
    }

    @Test
    void rejectsBlankIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> service.registerProduct(" ", ProductCategory.STANDARD));
        assertThrows(NullPointerException.class, () -> service.registerProduct("SKU-2", null));
        assertThrows(IllegalArgumentException.class, () -> service.reserve("", "SKU-1", 1));
    }
}
