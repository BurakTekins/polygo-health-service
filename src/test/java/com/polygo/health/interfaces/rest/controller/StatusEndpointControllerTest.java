package com.polygo.health.interfaces.rest.controller;

import com.polygo.health.application.service.StatusEndpointService;
import com.polygo.health.common.exception.GlobalExceptionHandler;
import com.polygo.health.interfaces.rest.response.StatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StatusEndpointControllerTest {
    private final StatusEndpointService service = mock(StatusEndpointService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new StatusEndpointController(service))
                .addPlaceholderValue("services.endpoints.status-base", "/api/status")
                .addPlaceholderValue("services.endpoints.status-audit", "/audit")
                .addPlaceholderValue("services.endpoints.status-service", "/service")
                .addPlaceholderValue("services.endpoints.status-market", "/market")
                .addPlaceholderValue("services.endpoints.status-market-fee", "/market-fee")
                .addPlaceholderValue("services.endpoints.status-wallet-balance", "/wallet-balance")
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void clearContext() { MDC.clear(); }

    @Test
    void serializesActualServiceHealth() throws Exception {
        when(service.getAllServiceStatus()).thenReturn(List.of(new StatusResponse(1L, "engine", false,
                null, null, null)));
        mvc.perform(get("/api/status/service")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("engine"))
                .andExpect(jsonPath("$[0].healthy").value(false));
    }

    @Test
    void noUnhealthyMarketsReturnsEmptyArray() throws Exception {
        when(service.getUnhealthyMarketStatus()).thenReturn(List.of());
        mvc.perform(get("/api/status/market")).andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(service).getUnhealthyMarketStatus();
    }

    @Test
    void unexpectedFailureDoesNotExposeInternalMessage() throws Exception {
        when(service.getAllServiceStatus()).thenThrow(new RuntimeException("private-database-detail"));
        mvc.perform(get("/api/status/service")).andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-database-detail"))));
    }
}
