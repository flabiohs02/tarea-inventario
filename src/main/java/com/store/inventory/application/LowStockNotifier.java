package com.store.inventory.application;

import com.store.inventory.api.StockAlertListener;
import com.store.inventory.domain.LowStockRule.LowStockAlert;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Objects;
import java.util.Optional;

/**
 * Delivers low stock alerts to purchasing. A failing channel must never break a customer's
 * reservation, so errors are logged and swallowed.
 */
public final class LowStockNotifier {

    private static final Logger LOG = System.getLogger(LowStockNotifier.class.getName());

    private final StockAlertListener listener;

    public LowStockNotifier(StockAlertListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
    }

    public void send(Optional<LowStockAlert> alert) {
        alert.ifPresent(this::send);
    }

    private void send(LowStockAlert alert) {
        try {
            listener.onLowStock(alert.sku(), alert.availableUnits());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Could not deliver low stock alert for " + alert.sku(), e);
        }
    }
}
