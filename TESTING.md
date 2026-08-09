# Automated testing

## Audit (2026-10-31)

Audited base commit: `127d3c08aafc279d104107a3fb3254d523f9fdaa`.

Spring Boot 3.5.7 / Java 21, REST endpoints, application services, JPA/PostgreSQL,
Flyway, asynchronous scheduled polling, and Kafka notification publication.
JUnit Jupiter, Mockito, Spring Test/MockMvc, and spring-kafka-test were already
available, but there was no src/test tree or checked-in CI workflow. The Maven
wrapper scripts were tracked without their required properties file.

## Added or repaired tests

- `JobHealthStatusServiceImplTest`: first status, recovery reuses an existing row, lookup failure avoids writes (3 cases).
- `NotificationFlushServiceTest`: empty queue, ordered message aggregation, synchronous producer failure leaves messages pending (3 cases).
- `AbstractRetryableFetchJobTest`: success and subsequent run, queued duplicate suppression with MDC propagation/cleanup, nonretryable failure and recovery, interrupted retry preserves the interrupt flag (4 cases). Controlled executors avoid wall-clock sleeps.
- `StatusEndpointControllerTest`: actual health serialization, empty market results, internal error details excluded from HTTP responses (3 cases). Standalone MockMvc exercises controller routing/serialization and real exception advice.
- `JobHealthPersistenceTest`: all 12 Flyway migrations on disposable PostgreSQL 16; validates only the job-health entity/repository; repeated upsert/recovery, database uniqueness, transactional rollback (3 cases).

## Running the suite

Java 21, Docker, and initial dependency/image access are required.
Run `sh ./mvnw --batch-mode --no-transfer-progress test` from this repository.
Added the missing `.mvn/wrapper/maven-wrapper.properties` matching the tracked
wrapper script and verified the wrapper with Maven 3.9.16.
Final local execution redirected Maven's wrapper/dependency caches into /tmp.
Testcontainers `junit-jupiter` and `postgresql` 1.21.3 are new test-scoped dependencies,
managed by Spring Boot. The test-only Docker API setting is 1.44. Integration tests
fail when Docker is unavailable; they are not conditionally skipped.

## Verified result (2026-10-31)

16 tests executed, 16 passed, 0 failures, 0 errors, 0 skipped.
16 cases were added in this change. Results come from actual local execution,
not an inferred GitHub Actions result.

## CI

`.github/workflows/tests.yml` runs on push, pull request, and manual dispatch.
Actions are pinned to verified commit SHAs, permissions are read-only, overlapping
runs are cancelled, and jobs have a 20-minute timeout. Java/frontend workflows
upload test reports even on failure. All four workflows passed actionlint 1.7.7.
GitHub-hosted execution has not been verified. Local test execution was completed
on 2026-10-31; commit metadata dates do not represent additional test runs.

## Remaining gaps and boundaries

- Broad entity validation discovered existing schema drift: `V7__create_config_parameters_table.sql:2` creates SERIAL, while `domain/model/ConfigParameters.java:27` uses Long/BIGINT. Production schema/entities were left unchanged. The integration test intentionally scopes JPA to JobHealthStatus and its repository, with schema validation enabled for that entity; it does not certify every entity mapping.
- `AbstractRetryableFetchJob.startAsyncJob` uses separate get/set operations on the running flag. Queued duplicate suppression is covered; simultaneous callers, executor rejection, and the complete 15s/60s retry schedule are not verified.
- `NotificationFlushService` marks messages sent after calling KafkaTemplate.send without awaiting the returned future. Synchronous failure is covered; asynchronous Kafka failure/delivery guarantees require separate work. No broker integration is claimed.
- Other health checks/configuration routes, authentication/authorization, distributed scheduler locking, and deployment behavior remain uncovered.
