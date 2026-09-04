package com.adobe.printservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadFactory;

/**
 * Configuration class for concurrency resources.
 * Provisions a centralized ThreadFactory for Virtual Threads, ensuring
 * observability (e.g., thread naming) and easy mocking during tests,
 * without relying on Spring's @Async proxy mechanics.
 */
@Configuration
public class AsyncConfig {

  @Bean
  public ThreadFactory virtualThreadFactory() {
    return Thread.ofVirtual()
        .name("print-vt-", 1) // Nombres claros para los logs
        .factory();
  }
}