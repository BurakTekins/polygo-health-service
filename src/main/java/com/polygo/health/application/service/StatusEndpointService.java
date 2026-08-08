package com.polygo.health.application.service;

import com.polygo.health.interfaces.rest.response.WalletBalanceStatusResponse;
import com.polygo.health.interfaces.rest.response.StatusResponse;

import java.util.List;

public interface StatusEndpointService {

    List<StatusResponse> getAllAuditStatus();

    List<StatusResponse> getAllServiceStatus();

    List<StatusResponse> getUnhealthyMarketStatus();

    List<StatusResponse> getAllMarketFeeStatus();

    List<WalletBalanceStatusResponse> getWalletBalanceStatus();
}
