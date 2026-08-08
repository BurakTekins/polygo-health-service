package com.polygo.health.application.service.impl;

import com.polygo.health.interfaces.rest.request.ConfigParametersRequest;
import com.polygo.health.interfaces.rest.response.ConfigParametersResponse;
import com.polygo.health.domain.model.ConfigParameters;
import com.polygo.health.infrastructure.persistence.jpa.repository.ConfigParametersRepository;
import com.polygo.health.application.service.ConfigParametersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ConfigParametersServiceImpl implements ConfigParametersService {

    @Autowired
    private ConfigParametersRepository repository;

    @Override
    public Map<String, String> getParametersFor(String serviceName) {
        return repository.findByServiceName(serviceName)
                .stream()
                .collect(Collectors.toMap(ConfigParameters::getConfigKey, ConfigParameters::getConfigValue));
    }

    @Override
    public String getConfigValue(String serviceName, String key) {
        return repository.findByServiceNameAndConfigKey(serviceName, key)
                .map(ConfigParameters::getConfigValue)
                .orElse(null);
    }

    @Override
    public List<String> getConfigList(String serviceName, String key) {
        String value = getConfigValue(serviceName, key);
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(","));
    }

    @Override
    public void updateConfigValue(String serviceName, String key, String newValue) {
        var existing = repository.findByServiceNameAndConfigKey(serviceName, key)
                .orElseThrow(() -> new RuntimeException("Config not found"));
        existing.setConfigValue(newValue);
        existing.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(existing);
    }

    public List<ConfigParametersResponse> getAllConfigs() {
        return repository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public List<ConfigParametersResponse> getAllConfigsForService(String serviceName) {
        return repository.findByServiceName(serviceName)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public ConfigParametersResponse updateConfigWithRequest(String serviceName, String key, ConfigParametersRequest request) {
        var existing = repository.findByServiceNameAndConfigKey(serviceName, key)
                .orElseThrow(() -> new RuntimeException("Config not found"));

        existing.setConfigValue(request.getValue());
        existing.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(existing);

        return mapToResponse(existing);
    }

    private ConfigParametersResponse mapToResponse(ConfigParameters config) {
        ConfigParametersResponse response = new ConfigParametersResponse();
        response.setId(config.getId());
        response.setServiceName(config.getServiceName());
        response.setConfigKey(config.getConfigKey());
        response.setDescription(config.getDescription());
        response.setConfigValue(config.getConfigValue());
        response.setCreatedAt(config.getCreatedAt());
        response.setUpdatedAt(config.getUpdatedAt());
        return response;
    }
}
