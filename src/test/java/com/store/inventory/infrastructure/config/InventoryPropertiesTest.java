package com.store.inventory.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.store.inventory.api.InventoryService;
import com.store.inventory.api.OrderLimitExceededException;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.Reservation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

class InventoryPropertiesTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private static final String[] ALL_CATEGORIES = {
        "inventory.categories.STANDARD.payment-window=15m",
        "inventory.categories.PRE_ORDER.payment-window=24h",
        "inventory.categories.FLASH_SALE.payment-window=5m",
        "inventory.categories.FLASH_SALE.max-units-per-order=2",
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class, InventoryConfiguration.class, FixedClockConfig.class);

    @Test
    void bindsRulesFromProperties() {
        runner.withPropertyValues(ALL_CATEGORIES)
                .withPropertyValues("inventory.low-stock-threshold=7")
                .run(context -> {
                    InventoryProperties props = context.getBean(InventoryProperties.class);
                    assertThat(props.lowStockThreshold()).isEqualTo(7);
                    assertThat(props.categories().get(ProductCategory.PRE_ORDER).paymentWindow())
                            .isEqualTo(Duration.ofHours(24));
                    assertThat(props.categories().get(ProductCategory.STANDARD).maxUnitsPerOrder()).isNull();
                });
    }

    @Test
    void configuredRulesDriveTheService() {
        runner.withPropertyValues(ALL_CATEGORIES)
                .withPropertyValues(
                        "inventory.categories.FLASH_SALE.payment-window=10m",
                        "inventory.categories.FLASH_SALE.max-units-per-order=3")
                .run(context -> {
                    InventoryService service = context.getBean(InventoryService.class);
                    service.registerProduct("FLASH", ProductCategory.FLASH_SALE);
                    service.addStock("FLASH", 10);

                    Reservation reservation = service.reserve("ORDER-1", "FLASH", 3);

                    assertThat(reservation.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
                    assertThrows(OrderLimitExceededException.class, () -> service.reserve("ORDER-2", "FLASH", 4));
                });
    }

    @Test
    void missingMaxUnitsMeansNoLimit() {
        runner.withPropertyValues(ALL_CATEGORIES).run(context -> {
            InventoryService service = context.getBean(InventoryService.class);
            service.registerProduct("STD", ProductCategory.STANDARD);
            service.addStock("STD", 1_000);

            assertThat(service.reserve("ORDER-1", "STD", 1_000).quantity()).isEqualTo(1_000);
        });
    }

    @Test
    void failsToStartWhenACategoryIsMissing() {
        runner.withPropertyValues(
                        "inventory.categories.STANDARD.payment-window=15m",
                        "inventory.categories.PRE_ORDER.payment-window=24h")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("FLASH_SALE"));
    }

    @Test
    void failsToStartWithInvalidValues() {
        runner.withPropertyValues(ALL_CATEGORIES)
                .withPropertyValues("inventory.categories.FLASH_SALE.max-units-per-order=0")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(ALL_CATEGORIES)
                .withPropertyValues("inventory.low-stock-threshold=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsToStartWithoutCategories() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(InventoryProperties.class)
    static class PropertiesConfig {
    }

    @Configuration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
