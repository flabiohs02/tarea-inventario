package com.store.inventory.domain;

import com.store.inventory.api.ProductCategory;
import java.util.Objects;

/**
 * A product in the warehouse.
 *
 * <p>{@code stockOnHand} counts the physical units that have not been sold yet. Units that are only
 * reserved are still on hand; they leave when their reservation is confirmed.
 */
public final class Product {

    private final String sku;
    private final ProductCategory category;
    private int stockOnHand;
    private boolean lowStockAlerted;

    public Product(String sku, ProductCategory category) {
        this.sku = requireText(sku, "sku");
        this.category = Objects.requireNonNull(category, "category must not be null");
    }

    public String sku() {
        return sku;
    }

    public ProductCategory category() {
        return category;
    }

    public int stockOnHand() {
        return stockOnHand;
    }

    public void addStock(int quantity) {
        requirePositive(quantity);
        stockOnHand = Math.addExact(stockOnHand, quantity);
    }

    /** Takes sold units out of the warehouse. They never come back to stock. */
    public void removeSoldUnits(int quantity) {
        requirePositive(quantity);
        if (quantity > stockOnHand) {
            throw new IllegalStateException(
                    "Cannot sell " + quantity + " units of " + sku + ", only " + stockOnHand + " on hand");
        }
        stockOnHand -= quantity;
    }

    /**
     * Marks that purchasing has already been warned about low stock.
     *
     * @return {@code true} if the product had not been flagged yet, so an alert must be sent
     */
    public boolean markLowStockAlerted() {
        if (lowStockAlerted) {
            return false;
        }
        lowStockAlerted = true;
        return true;
    }

    /** Allows a new low stock alert to be sent in the future (after a restock). */
    public void rearmLowStockAlert() {
        lowStockAlerted = false;
    }

    public boolean isLowStockAlerted() {
        return lowStockAlerted;
    }

    static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, was " + quantity);
        }
    }
}
