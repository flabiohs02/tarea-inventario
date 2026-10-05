package com.store.inventory.infrastructure.notification;

import com.store.inventory.api.StockAlertListener;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/** Temporary channel: writes low stock alerts to the log until the email channel is available. */
public final class LoggingStockAlertListener implements StockAlertListener {

    private static final Logger LOG = System.getLogger(LoggingStockAlertListener.class.getName());

    @Override
    public void onLowStock(String sku, int availableUnits) {
        LOG.log(Level.WARNING, "LOW STOCK: {0} has {1} units available", sku, availableUnits);
    }
}
