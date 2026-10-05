package com.store.inventory.infrastructure.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.store.inventory.infrastructure.InventoryApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** The service lives in memory and is shared by the tests, so each test uses its own SKUs and orders. */
@SpringBootTest(classes = InventoryApplication.class)
@AutoConfigureMockMvc
class InventoryControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void fullFlowRegisterRestockReserveConfirm() throws Exception {
        postJson("/api/products", """
                {"sku": "FLOW-1", "category": "STANDARD"}""").andExpect(status().isCreated());
        postJson("/api/products/FLOW-1/stock", """
                {"quantity": 10}""").andExpect(status().isNoContent());

        postJson("/api/reservations", """
                {"orderId": "FLOW-ORDER-1", "sku": "FLOW-1", "quantity": 3}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("FLOW-ORDER-1"))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.expiresAt").exists());

        mvc.perform(get("/api/products/FLOW-1/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(7));

        mvc.perform(post("/api/reservations/FLOW-ORDER-1/confirmation")).andExpect(status().isNoContent());
        mvc.perform(get("/api/products/FLOW-1/availability")).andExpect(jsonPath("$.available").value(7));
    }

    @Test
    void insufficientStockIsConflict() throws Exception {
        registerWithStock("LOW-1", "STANDARD", 2);

        postJson("/api/reservations", """
                {"orderId": "LOW-ORDER-1", "sku": "LOW-1", "quantity": 3}""")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Insufficient stock"))
                .andExpect(jsonPath("$.detail").value(containsString("requested 3, available 2")));
    }

    @Test
    void orderLimitIsUnprocessable() throws Exception {
        registerWithStock("FLASH-1", "FLASH_SALE", 10);

        postJson("/api/reservations", """
                {"orderId": "FLASH-ORDER-1", "sku": "FLASH-1", "quantity": 3}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Order limit exceeded"));
    }

    @Test
    void confirmingUnknownOrderIsConflict() throws Exception {
        mvc.perform(post("/api/reservations/NOPE/confirmation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("no active reservation")));
    }

    @Test
    void addingStockToUnknownProductIsBadRequest() throws Exception {
        postJson("/api/products/GHOST/stock", """
                {"quantity": 5}""").andExpect(status().isBadRequest());
    }

    @Test
    void invalidBodyIsBadRequest() throws Exception {
        postJson("/api/reservations", """
                {"orderId": "", "sku": "X", "quantity": 0}""")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        postJson("/api/products", """
                {"sku": "X", "category": "NOT_A_CATEGORY"}""").andExpect(status().isBadRequest());
    }

    @Test
    void resentOrderReturnsTheSameReservation() throws Exception {
        registerWithStock("RETRY-1", "STANDARD", 10);
        String body = """
                {"orderId": "RETRY-ORDER-1", "sku": "RETRY-1", "quantity": 2}""";

        postJson("/api/reservations", body).andExpect(status().isCreated());
        postJson("/api/reservations", body).andExpect(status().isCreated());

        mvc.perform(get("/api/products/RETRY-1/availability")).andExpect(jsonPath("$.available").value(8));
    }

    private void registerWithStock(String sku, String category, int quantity) throws Exception {
        postJson("/api/products", "{\"sku\": \"%s\", \"category\": \"%s\"}".formatted(sku, category))
                .andExpect(status().isCreated());
        postJson("/api/products/" + sku + "/stock", "{\"quantity\": %d}".formatted(quantity))
                .andExpect(status().isNoContent());
    }

    private ResultActions postJson(String path, String json) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
