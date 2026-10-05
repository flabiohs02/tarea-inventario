package com.store.inventory.domain;

import com.store.inventory.api.OrderLimitExceededException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Business rules that depend on a product category: how long the customer has to pay and how many
 * units a single order may reserve.
 */
public record CategoryPolicy(Duration paymentWindow, int maxUnitsPerOrder) {

    public static final int UNLIMITED = Integer.MAX_VALUE;

    public CategoryPolicy {
        Objects.requireNonNull(paymentWindow, "paymentWindow must not be null");
        if (paymentWindow.isNegative() || paymentWindow.isZero()) {
            throw new IllegalArgumentException("paymentWindow must be positive");
        }
        if (maxUnitsPerOrder <= 0) {
            throw new IllegalArgumentException("maxUnitsPerOrder must be positive");
        }
    }

    public static CategoryPolicy unlimited(Duration paymentWindow) {
        return new CategoryPolicy(paymentWindow, UNLIMITED);
    }

    public static CategoryPolicy limitedTo(int maxUnitsPerOrder, Duration paymentWindow) {
        return new CategoryPolicy(paymentWindow, maxUnitsPerOrder);
    }

    public void checkOrderLimit(String sku, int requested) {
        if (requested > maxUnitsPerOrder) {
            throw new OrderLimitExceededException(sku, requested, maxUnitsPerOrder);
        }
    }

    public Instant expirationFrom(Instant reservedAt) {
        return reservedAt.plus(paymentWindow);
    }
}
