package com.store.inventory.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AvailabilityResponse(
        @Schema(example = "SKU-1") String sku,
        @Schema(example = "8", description = "Stock minus active reservations") int available) {
}
