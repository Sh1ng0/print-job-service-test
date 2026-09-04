package com.adobe.printservice.config;

import com.adobe.printservice.model.JobStatus;
import com.adobe.printservice.repository.JobRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobMetricsBinderTest {

  @Mock
  private JobRepository jobRepository;

  @InjectMocks
  private JobMetricsBinder jobMetricsBinder;

  @Test
  @DisplayName("Metrics Binder - Registers gauges correctly for all job statuses")
  void shouldRegisterGaugesForAllJobStatuses() {
    // Arrange
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    when(jobRepository.countByStatus(JobStatus.QUEUED)).thenReturn(42L);
    when(jobRepository.countByStatus(JobStatus.PROCESSING)).thenReturn(5L);

    // Act
    jobMetricsBinder.bindTo(registry);

    // Assert
    double queuedCount = registry.get("printservice.jobs.count")
        .tag("status", "QUEUED")
        .gauge()
        .value();

    double processingCount = registry.get("printservice.jobs.count")
        .tag("status", "PROCESSING")
        .gauge()
        .value();

    assertThat(queuedCount).isEqualTo(42.0);
    assertThat(processingCount).isEqualTo(5.0);
  }
}