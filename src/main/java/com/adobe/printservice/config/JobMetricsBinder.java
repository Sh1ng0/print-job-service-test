package com.adobe.printservice.config;

import com.adobe.printservice.model.JobStatus;
import com.adobe.printservice.repository.JobRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * Binds custom business metrics to the Spring Boot Actuator registry.
 * Exposes the count of jobs grouped by their current status.
 */
@Component
public class JobMetricsBinder implements MeterBinder {

  private final JobRepository jobRepository;

  public JobMetricsBinder(JobRepository jobRepository) {
    this.jobRepository = jobRepository;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    for (JobStatus status : JobStatus.values()) {
      Gauge.builder("printservice.jobs.count", () -> jobRepository.countByStatus(status))
          .description("Current number of print jobs")
          .tag("status", status.name())
          .register(registry);
    }
  }
}