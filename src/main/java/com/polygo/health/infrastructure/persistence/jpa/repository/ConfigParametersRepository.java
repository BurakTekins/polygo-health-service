package com.polygo.health.infrastructure.persistence.jpa.repository;

import com.polygo.health.domain.model.ConfigParameters;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfigParametersRepository extends JpaRepository<ConfigParameters, Long> {

    List<ConfigParameters> findByServiceName(String serviceName);

    Optional<ConfigParameters> findByServiceNameAndConfigKey(String serviceName, String configKey);
}
