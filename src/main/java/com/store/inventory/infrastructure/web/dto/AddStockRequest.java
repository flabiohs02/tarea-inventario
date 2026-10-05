package com.store.inventory.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddStockRequest(@Schema(example = "10") @NotNull @Positive Integer quantity) {
}
