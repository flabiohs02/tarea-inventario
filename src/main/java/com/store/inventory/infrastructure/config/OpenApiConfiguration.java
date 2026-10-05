package com.store.inventory.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger / OpenAPI metadata, taken from {@code inventory.api.*} in application.properties. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI inventoryOpenApi(InventoryProperties properties) {
        InventoryProperties.ApiInfo api = properties.api();
        return new OpenAPI().info(new Info()
                .title(api.title())
                .version(api.version())
                .description(api.description()));
    }
}
