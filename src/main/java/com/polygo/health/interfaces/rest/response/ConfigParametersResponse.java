package com.polygo.health.interfaces.rest.response;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class ConfigParametersResponse {
    private Long id;
    private String serviceName;
    private String configKey;
    private String description;
    private String configValue;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
