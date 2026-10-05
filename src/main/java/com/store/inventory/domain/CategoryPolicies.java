package com.store.inventory.domain;

import com.store.inventory.api.ProductCategory;
import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Catalog of rules per category.
 *
 * <p><b>Adding a new seasonal category:</b> add the value to {@link ProductCategory} and register its
 * policy in {@link #defaults()}. Nothing else needs to change. If a category is forgotten, creating
 * the service fails immediately instead of failing on the first order.
 */
public final class CategoryPolicies {

    private final Map<ProductCategory, CategoryPolicy> policies;

    public CategoryPolicies(Map<ProductCategory, CategoryPolicy> policies) {
        EnumMap<ProductCategory, CategoryPolicy> copy = new EnumMap<>(ProductCategory.class);
        copy.putAll(policies);
        Set<ProductCategory> missing = EnumSet.allOf(ProductCategory.class);
        missing.removeAll(copy.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing policy for categories: " + missing);
        }
        this.policies = copy;
    }

    public static CategoryPolicies defaults() {
        return new CategoryPolicies(Map.of(
                ProductCategory.STANDARD, CategoryPolicy.unlimited(Duration.ofMinutes(15)),
                // Paid by bank transfer, which takes longer.
                ProductCategory.PRE_ORDER, CategoryPolicy.unlimited(Duration.ofHours(24)),
                ProductCategory.FLASH_SALE, CategoryPolicy.limitedTo(2, Duration.ofMinutes(5))));
    }

    public CategoryPolicy policyFor(ProductCategory category) {
        return policies.get(category);
    }
}
