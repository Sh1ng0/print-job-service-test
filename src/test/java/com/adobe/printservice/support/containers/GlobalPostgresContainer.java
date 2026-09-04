package com.adobe.printservice.support.containers;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Singleton implementation (Bloch Enum) for the PostgreSQL test container.
 * Guarantees that only a single instance of the database is spun up for the entire
 * test suite execution, drastically reducing context load times.
 * Relies on Testcontainers' reuse feature for optimal performance.
 */
public enum GlobalPostgresContainer {

  INSTANCE;

  private final PostgreSQLContainer<?> container;

  @SuppressWarnings("resource") // Ryuk will handle container teardown when the JVM stops
  GlobalPostgresContainer() {
    container = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("print_service_test_db")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);

    container.start();
  }

  public PostgreSQLContainer<?> getContainer() {
    return container;
  }
}