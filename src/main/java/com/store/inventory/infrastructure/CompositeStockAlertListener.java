package com.store.inventory.infrastructure;

import com.store.inventory.api.StockAlertListener;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;

/**
 * Sends each alert to several channels (email today; SMS, Slack, ... tomorrow). Adding a channel
 * means writing a new {@link StockAlertListener} and adding it here. One failing channel does not
 * prevent the others from being notified.
 */
public final class CompositeStockAlertListener implements StockAlertListener {

    private static final Logger LOG = System.getLogger(CompositeStockAlertListener.class.getName());

    private final List<StockAlertListener> channels;

    public CompositeStockAlertListener(List<StockAlertListener> channels) {
        this.channels = List.copyOf(channels);
    }

    public static CompositeStockAlertListener of(StockAlertListener... channels) {
        return new CompositeStockAlertListener(List.of(channels));
    }

    @Override
    public void onLowStock(String sku, int availableUnits) {
        for (StockAlertListener channel : channels) {
            try {
                channel.onLowStock(sku, availableUnits);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Alert channel " + channel + " failed for " + sku, e);
            }
        }
    }
}
