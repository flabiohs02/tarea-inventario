package com.store.inventory.infrastructure.config;

import com.store.inventory.Inventory;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.StockAlertListener;
import com.store.inventory.infrastructure.CompositeStockAlertListener;
import com.store.inventory.infrastructure.notification.LoggingStockAlertListener;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free inventory service into Spring using the configured rules. */
@Configuration(proxyBeanMethods = false)
public class InventoryConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Add new channels (email, SMS, Slack...) to this list. */
    @Bean
    StockAlertListener stockAlertListener() {
        return CompositeStockAlertListener.of(new LoggingStockAlertListener());
    }

    @Bean
    InventoryService inventoryService(Clock clock, StockAlertListener alertListener, InventoryProperties properties) {
        return Inventory.create(
                clock, alertListener, properties.toCategoryPolicies(), properties.toLowStockRule());
    }
}
