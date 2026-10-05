package com.store.inventory.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReserveRequest(
        @Schema(example = "ORDER-1", description = "Resending the same order is safe (idempotent)")
        @NotBlank String orderId,
        @Schema(example = "SKU-1") @NotBlank String sku,
        @Schema(example = "2") @NotNull @Positive Integer quantity) {
}
