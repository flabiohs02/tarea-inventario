package com.store.inventory.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.store.inventory.api.ProductCategory;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CategoryPoliciesTest {

    @Test
    void defaultsCoverEveryCategory() {
        assertDoesNotThrow(CategoryPolicies::defaults);
    }

    @Test
    void failsFastWhenACategoryHasNoPolicy() {
        Map<ProductCategory, CategoryPolicy> incomplete =
                Map.of(ProductCategory.STANDARD, CategoryPolicy.unlimited(Duration.ofMinutes(15)));

        assertThrows(IllegalArgumentException.class, () -> new CategoryPolicies(incomplete));
    }

    @Test
    void rejectsInvalidPolicies() {
        assertThrows(IllegalArgumentException.class, () -> CategoryPolicy.unlimited(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> CategoryPolicy.limitedTo(0, Duration.ofMinutes(5)));
    }
}
