package com.store.inventory.infrastructure.web.dto;

import com.store.inventory.api.Reservation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record ReservationResponse(
        @Schema(example = "ORDER-1") String orderId,
        @Schema(example = "SKU-1") String sku,
        @Schema(example = "2") int quantity,
        @Schema(description = "Deadline to pay; afterwards the units are released") Instant expiresAt) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.orderId(), reservation.sku(), reservation.quantity(), reservation.expiresAt());
    }
}
