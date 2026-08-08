package com.polygo.health.interfaces.rest.controller;

import com.polygo.health.interfaces.rest.response.WalletBalanceStatusResponse;
import com.polygo.health.interfaces.rest.response.StatusResponse;
import com.polygo.health.application.service.StatusEndpointService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${services.endpoints.status-base}")
@RequiredArgsConstructor
public class StatusEndpointController {

    private final StatusEndpointService statusEndpointService;

    @GetMapping("${services.endpoints.status-audit}")
    public List<StatusResponse> getAllAuditStatus() {
        return statusEndpointService.getAllAuditStatus();
    }

    @GetMapping("${services.endpoints.status-service}")
    public List<StatusResponse> getAllServiceStatus() {
        return statusEndpointService.getAllServiceStatus();
    }

    @GetMapping("${services.endpoints.status-market}")
    public List<StatusResponse> getAllMarketStatus() {
        return statusEndpointService.getUnhealthyMarketStatus();
    }

    @GetMapping("${services.endpoints.status-market-fee}")
    public List<StatusResponse> getAllMarketFeeStatus() {
        return statusEndpointService.getAllMarketFeeStatus();
    }

    @GetMapping("${services.endpoints.status-wallet-balance}")
    public List<WalletBalanceStatusResponse> getWalletBalanceStatus() {
        return statusEndpointService.getWalletBalanceStatus();
    }
}
