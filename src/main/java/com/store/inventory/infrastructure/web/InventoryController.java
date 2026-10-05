package com.store.inventory.infrastructure.web;

import com.store.inventory.api.InventoryService;
import com.store.inventory.infrastructure.web.dto.AddStockRequest;
import com.store.inventory.infrastructure.web.dto.AvailabilityResponse;
import com.store.inventory.infrastructure.web.dto.RegisterProductRequest;
import com.store.inventory.infrastructure.web.dto.ReservationResponse;
import com.store.inventory.infrastructure.web.dto.ReserveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter: translates JSON requests into calls to the {@link InventoryService} contract. */
@RestController
@RequestMapping("/api")
public class InventoryController {

    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @Tag(name = "Products")
    @Operation(summary = "Register a product and its category")
    @ApiResponse(responseCode = "201", description = "Registered (repeating it with the same category is harmless)")
    @ApiResponse(responseCode = "400", description = "Invalid data")
    @ApiResponse(responseCode = "409", description = "Already registered with another category")
    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public void registerProduct(@Valid @RequestBody RegisterProductRequest request) {
        inventory.registerProduct(request.sku(), request.category());
    }

    @Tag(name = "Products")
    @Operation(summary = "Add units of a registered product to the warehouse")
    @ApiResponse(responseCode = "204", description = "Stock added")
    @ApiResponse(responseCode = "400", description = "Quantity not positive or product not registered")
    @PostMapping("/products/{sku}/stock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addStock(@PathVariable("sku") String sku, @Valid @RequestBody AddStockRequest request) {
        inventory.addStock(sku, request.quantity());
    }

    @Tag(name = "Products")
    @Operation(summary = "Units that can still be reserved (0 for unknown products)")
    @ApiResponse(responseCode = "200", description = "Available units")
    @GetMapping("/products/{sku}/availability")
    public AvailabilityResponse available(@PathVariable("sku") String sku) {
        return new AvailabilityResponse(sku, inventory.available(sku));
    }

    @Tag(name = "Reservations")
    @Operation(summary = "Reserve units for an order while the customer pays",
            description = "The reservation expires according to the product category unless it is confirmed.")
    @ApiResponse(responseCode = "201", description = "Reserved (or the existing reservation, if the order was resent)")
    @ApiResponse(responseCode = "400", description = "Invalid data")
    @ApiResponse(responseCode = "409", description = "Not enough stock, or the order already reserved something else")
    @ApiResponse(responseCode = "422", description = "The category does not allow that many units per order")
    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(@Valid @RequestBody ReserveRequest request) {
        return ReservationResponse.from(inventory.reserve(request.orderId(), request.sku(), request.quantity()));
    }

    @Tag(name = "Reservations")
    @Operation(summary = "Confirm the reservation of a paid order; the units become sold")
    @ApiResponse(responseCode = "204", description = "Confirmed (confirming twice is harmless)")
    @ApiResponse(responseCode = "409", description = "The order has no active reservation (unknown or expired)")
    @PostMapping("/reservations/{orderId}/confirmation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@PathVariable("orderId") String orderId) {
        inventory.confirm(orderId);
    }
}
