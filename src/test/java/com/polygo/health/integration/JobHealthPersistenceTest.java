package com.polygo.health.integration;

import com.polygo.health.domain.model.JobHealthStatus;
import com.polygo.health.infrastructure.persistence.jpa.repository.JobHealthStatusRepository;
import com.polygo.health.application.service.impl.JobHealthStatusServiceImpl;
import com.polygo.health.domain.enums.JobType;
import com.polygo.health.domain.enums.JobStatus;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {
    "spring.config.location=optional:classpath:/test-isolated.properties",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.baseline-on-migrate=false",
    "spring.flyway.schemas=health",
    "spring.flyway.default-schema=health",
    "spring.jpa.properties.hibernate.default_schema=health"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = JobHealthPersistenceTest.Config.class)
@Testcontainers
class JobHealthPersistenceTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackageClasses = JobHealthStatusRepository.class,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = JobHealthStatusRepository.class))
    @Import(JobHealthStatusServiceImpl.class)
    static class Config {
        @Bean
        org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes managedTypes() {
            return org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes.of(JobHealthStatus.class.getName());
        }
    }

    @Autowired JobHealthStatusRepository repository;
    @Autowired JobHealthStatusServiceImpl service;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void repeatedChecksPersistRecoveryWithoutDuplicateJobRows() {
        service.upsert(JobType.SERVICE_STATUS, JobStatus.ERROR, "offline");
        entityManager.flush();
        entityManager.clear();
        Long firstId = repository.findByJobType(JobType.SERVICE_STATUS).orElseThrow().getId();
        service.upsert(JobType.SERVICE_STATUS, JobStatus.OK, "recovered");
        entityManager.flush();
        entityManager.clear();
        var stored = repository.findByJobType(JobType.SERVICE_STATUS).orElseThrow();
        assertThat(repository.count()).isEqualTo(1);
        assertThat(stored.getId()).isEqualTo(firstId);
        assertThat(stored.getStatus()).isEqualTo(JobStatus.OK);
        assertThat(stored.getMessage()).isEqualTo("recovered");
    }

    @Test
    void databaseRejectsDuplicateJobType() {
        service.upsert(JobType.SERVICE_STATUS, JobStatus.OK, "ok");
        entityManager.flush();
        var duplicate = JobHealthStatus.builder().jobType(JobType.SERVICE_STATUS)
                .status(JobStatus.ERROR).message("duplicate").build();
        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void healthUpdateRollsBackWithCallerTransaction() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.upsert(JobType.SERVICE_STATUS, JobStatus.ERROR, "offline");
            entityManager.flush();
            throw new IllegalStateException("abort operation");
        })).isInstanceOf(IllegalStateException.class).hasMessage("abort operation");
        assertThat(repository.count()).isZero();
    }
}
