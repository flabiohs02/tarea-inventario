package com.store.inventory.domain;

import java.util.Optional;

/**
 * Decides when purchasing must be warned about low stock.
 *
 * <ul>
 *   <li>An alert is raised when available units drop to {@code threshold} or below.</li>
 *   <li>It is raised only once; it is re-armed when a restock leaves the product above the threshold.</li>
 *   <li>Units coming back from expired reservations are not a restock and do not re-arm the alert.</li>
 * </ul>
 */
public final class LowStockRule {

    public static final int DEFAULT_THRESHOLD = 5;

    private final int threshold;

    public LowStockRule(int threshold) {
        if (threshold < 0) {
            throw new IllegalArgumentException("threshold must not be negative");
        }
        this.threshold = threshold;
    }

    public static LowStockRule defaultRule() {
        return new LowStockRule(DEFAULT_THRESHOLD);
    }

    /** Call after units of {@code product} were reserved. */
    public Optional<LowStockAlert> afterReservation(Product product, int availableUnits) {
        if (availableUnits <= threshold && product.markLowStockAlerted()) {
            return Optional.of(new LowStockAlert(product.sku(), availableUnits));
        }
        return Optional.empty();
    }

    /** Call after stock was added to {@code product}. */
    public Optional<LowStockAlert> afterRestock(Product product, int availableUnits) {
        if (availableUnits > threshold) {
            product.rearmLowStockAlert();
            return Optional.empty();
        }
        return afterReservation(product, availableUnits);
    }

    public record LowStockAlert(String sku, int availableUnits) {
    }
}
