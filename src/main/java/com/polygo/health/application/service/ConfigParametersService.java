package com.polygo.health.application.service;

import com.polygo.health.interfaces.rest.request.ConfigParametersRequest;
import com.polygo.health.interfaces.rest.response.ConfigParametersResponse;

import java.util.List;
import java.util.Map;

public interface ConfigParametersService {

    Map<String, String> getParametersFor(String serviceName);

    String getConfigValue(String serviceName, String key);

    List<String> getConfigList(String serviceName, String key);

    void updateConfigValue(String serviceName, String key, String newValue);

    List<ConfigParametersResponse> getAllConfigs();

    List<ConfigParametersResponse> getAllConfigsForService(String serviceName);

    ConfigParametersResponse updateConfigWithRequest(String serviceName, String key, ConfigParametersRequest request);
}
