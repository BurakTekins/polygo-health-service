package com.polygo.health.application.service.impl;

import com.polygo.health.infrastructure.persistence.jpa.repository.*;
import com.polygo.health.interfaces.rest.response.WalletBalanceStatusResponse;
import com.polygo.health.interfaces.rest.response.StatusResponse;
import com.polygo.health.application.service.StatusEndpointService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StatusEndpointServiceImpl implements StatusEndpointService {

        private final AuditStatusRepository auditStatusRepository;
        private final ServiceStatusRepository serviceStatusRepository;
        private final MarketStatusRepository marketStatusRepository;
        private final MarketFeeStatusRepository marketFeeStatusRepository;
        private final WalletBalanceStatusRepository walletBalanceStatusRepository;

        @Override
        public List<StatusResponse> getAllAuditStatus() {
                return auditStatusRepository.findAll()
                                .stream()
                                .map(s -> new StatusResponse(
                                                s.getId(),
                                                s.getServiceName(),
                                                s.isHealthy(),
                                                s.getUpdatedAt(),
                                                s.getLastStatusChangeAt(),
                                                s.getLastNotificationSentAt()))
                                .toList();
        }

        @Override
        public List<StatusResponse> getAllServiceStatus() {
                return serviceStatusRepository.findAll()
                                .stream()
                                .map(s -> new StatusResponse(
                                                s.getId(),
                                                s.getServiceName(),
                                                s.isHealthy(),
                                                s.getUpdatedAt(),
                                                s.getLastStatusChangeAt(),
                                                s.getLastNotificationSentAt()))
                                .toList();
        }

        @Override
        public List<StatusResponse> getUnhealthyMarketStatus() {
            return marketStatusRepository.findByHealthyFalse()
                                .stream()
                                .map(s -> new StatusResponse(
                                                s.getId(),
                                                s.getMarketName(),
                                                s.isHealthy(),
                                                s.getUpdatedAt(),
                                                s.getLastStatusChangeAt(),
                                                s.getLastNotificationSentAt()))
                                .toList();
        }

        @Override
        public List<StatusResponse> getAllMarketFeeStatus() {
                return marketFeeStatusRepository.findAll()
                                .stream()
                                .map(s -> new StatusResponse(
                                                s.getId(),
                                                s.getVenueName(),
                                                s.isHealthy(),
                                                s.getUpdatedAt(),
                                                s.getLastStatusChangeAt(),
                                                s.getLastNotificationSentAt()))
                                .toList();
        }

        @Override
        public List<WalletBalanceStatusResponse> getWalletBalanceStatus() {
                return walletBalanceStatusRepository.findAll()
                                .stream()
                                .map(s -> new WalletBalanceStatusResponse(
                                                s.getVenueCode(),
                                                s.getLastSnapshotTime(),
                                                s.isHealthy(),
                                                s.getCheckedAt()))
                                .toList();
        }
}
