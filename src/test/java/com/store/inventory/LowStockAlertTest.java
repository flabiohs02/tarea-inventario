package com.store.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.store.inventory.api.InventoryService;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.infrastructure.CompositeStockAlertListener;
import com.store.inventory.support.MutableClock;
import com.store.inventory.support.RecordingAlertListener;
import com.store.inventory.support.RecordingAlertListener.Alert;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LowStockAlertTest {

    private final MutableClock clock = MutableClock.startingAt("2026-01-01T10:00:00Z");
    private final RecordingAlertListener purchasing = new RecordingAlertListener();
    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = Inventory.create(clock, purchasing);
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);
    }

    @Test
    void noAlertWhileAboveThreshold() {
        service.reserve("ORDER-1", "SKU-1", 4);

        assertTrue(purchasing.alerts().isEmpty());
    }

    @Test
    void alertsWhenFiveOrFewerUnitsRemain() {
        service.reserve("ORDER-1", "SKU-1", 5);

        assertEquals(List.of(new Alert("SKU-1", 5)), purchasing.alerts());
    }

    @Test
    void doesNotRepeatTheAlertUntilRestocked() {
        service.reserve("ORDER-1", "SKU-1", 5);
        service.reserve("ORDER-2", "SKU-1", 2);
        service.confirm("ORDER-1");
        clock.advance(Duration.ofMinutes(15)); // ORDER-2 expires, units come back: not a restock
        service.reserve("ORDER-3", "SKU-1", 4);

        assertEquals(List.of(new Alert("SKU-1", 5)), purchasing.alerts());
    }

    @Test
    void restockAboveThresholdReArmsTheAlert() {
        service.reserve("ORDER-1", "SKU-1", 6);
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-2", "SKU-1", 12);

        assertEquals(List.of(new Alert("SKU-1", 4), new Alert("SKU-1", 2)), purchasing.alerts());
    }

    @Test
    void restockThatStaysLowDoesNotRepeatTheAlert() {
        service.reserve("ORDER-1", "SKU-1", 8);
        service.addStock("SKU-1", 1);

        assertEquals(List.of(new Alert("SKU-1", 2)), purchasing.alerts());
    }

    @Test
    void newProductWithLowInitialStockAlerts() {
        service.registerProduct("SKU-2", ProductCategory.STANDARD);
        service.addStock("SKU-2", 3);

        assertEquals(List.of(new Alert("SKU-2", 3)), purchasing.alerts());
    }

    @Test
    void failingAlertChannelDoesNotBreakTheReservation() {
        InventoryService withBrokenEmail = Inventory.create(clock, (sku, available) -> {
            throw new RuntimeException("mail server down");
        });
        withBrokenEmail.registerProduct("SKU-1", ProductCategory.STANDARD);
        withBrokenEmail.addStock("SKU-1", 6);

        withBrokenEmail.reserve("ORDER-1", "SKU-1", 3);

        assertEquals(3, withBrokenEmail.available("SKU-1"));
    }

    @Test
    void alertsCanGoToSeveralChannels() {
        RecordingAlertListener email = new RecordingAlertListener();
        RecordingAlertListener slack = new RecordingAlertListener();
        InventoryService multiChannel = Inventory.create(clock, CompositeStockAlertListener.of(
                email, (sku, available) -> { throw new RuntimeException("broken channel"); }, slack));
        multiChannel.registerProduct("SKU-1", ProductCategory.STANDARD);
        multiChannel.addStock("SKU-1", 6);

        multiChannel.reserve("ORDER-1", "SKU-1", 1);

        assertEquals(List.of(new Alert("SKU-1", 5)), email.alerts());
        assertEquals(List.of(new Alert("SKU-1", 5)), slack.alerts());
    }
}
