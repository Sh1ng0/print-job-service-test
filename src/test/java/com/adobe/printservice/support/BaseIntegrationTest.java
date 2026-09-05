package com.adobe.printservice.support;

import com.adobe.printservice.support.containers.GlobalPostgresContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Abstract base class for all integration tests requiring a database context.
 * Injects the connection details from the singleton GlobalPostgresContainer
 * dynamically into the Spring ApplicationContext.
 */
@SpringBootTest
public abstract class BaseIntegrationTest {

  static {
    GlobalPostgresContainer.INSTANCE.getContainer();
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    PostgreSQLContainer<?> postgres = GlobalPostgresContainer.INSTANCE.getContainer();

    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("worker.poll.interval.ms", () -> "10");
  }
}