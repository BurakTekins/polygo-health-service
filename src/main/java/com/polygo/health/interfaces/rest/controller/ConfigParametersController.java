package com.polygo.health.interfaces.rest.controller;

import com.polygo.health.interfaces.rest.request.ConfigParametersRequest;
import com.polygo.health.interfaces.rest.response.ConfigParametersResponse;
import com.polygo.health.application.service.ConfigParametersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${services.endpoints.config-base}")
public class ConfigParametersController {

    @Autowired
    private ConfigParametersService service;

    @GetMapping
    public List<ConfigParametersResponse> getAllConfigs() {
        return service.getAllConfigs();
    }

    @GetMapping("${services.endpoints.config-service}")
    public List<ConfigParametersResponse> getAllConfigs(@PathVariable String serviceName) {
        return service.getAllConfigsForService(serviceName);
    }

    @PutMapping("${services.endpoints.config-entry}")
    public ConfigParametersResponse updateConfig(@PathVariable String serviceName,
                                                 @PathVariable String key,
                                                 @RequestBody ConfigParametersRequest request) {
        return service.updateConfigWithRequest(serviceName, key, request);
    }
}
