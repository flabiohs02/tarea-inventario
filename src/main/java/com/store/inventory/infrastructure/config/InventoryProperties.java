package com.store.inventory.infrastructure.config;

import com.store.inventory.api.ProductCategory;
import com.store.inventory.domain.CategoryPolicies;
import com.store.inventory.domain.CategoryPolicy;
import com.store.inventory.domain.LowStockRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Business rules read from {@code application.properties} (prefix {@code inventory}).
 *
 * <pre>
 * inventory.low-stock-threshold=5
 * inventory.categories.FLASH_SALE.payment-window=5m
 * inventory.categories.FLASH_SALE.max-units-per-order=2   # omit for "no limit"
 * </pre>
 */
@Validated
@ConfigurationProperties("inventory")
public record InventoryProperties(
        @DefaultValue("5") @PositiveOrZero int lowStockThreshold,
        @NotEmpty Map<ProductCategory, @Valid @NotNull CategoryProperties> categories,
        @DefaultValue @Valid ApiInfo api) {

    public record CategoryProperties(@NotNull Duration paymentWindow, @Positive Integer maxUnitsPerOrder) {

        CategoryPolicy toPolicy() {
            return maxUnitsPerOrder == null
                    ? CategoryPolicy.unlimited(paymentWindow)
                    : CategoryPolicy.limitedTo(maxUnitsPerOrder, paymentWindow);
        }
    }

    /** Information shown at the top of Swagger UI. */
    public record ApiInfo(
            @DefaultValue("Inventory Reservations API") String title,
            @DefaultValue("1.0.0") String version,
            @DefaultValue("") String description) {
    }

    /** Fails if any {@link ProductCategory} is missing, so a forgotten category stops the startup. */
    public CategoryPolicies toCategoryPolicies() {
        Map<ProductCategory, CategoryPolicy> policies = new EnumMap<>(ProductCategory.class);
        categories.forEach((category, props) -> policies.put(category, props.toPolicy()));
        return new CategoryPolicies(policies);
    }

    public LowStockRule toLowStockRule() {
        return new LowStockRule(lowStockThreshold);
    }
}
