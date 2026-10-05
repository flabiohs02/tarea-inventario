package com.store.inventory.infrastructure.web.dto;

import com.store.inventory.api.ProductCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterProductRequest(
        @Schema(example = "SKU-1") @NotBlank String sku,
        @Schema(example = "FLASH_SALE") @NotNull ProductCategory category) {
}
