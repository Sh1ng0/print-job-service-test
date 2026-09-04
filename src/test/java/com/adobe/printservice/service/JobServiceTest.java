package com.adobe.printservice.service;

import com.adobe.printservice.domain.RenderResult;
import com.adobe.printservice.model.Job;
import com.adobe.printservice.model.JobStatus;
import com.adobe.printservice.repository.JobRepository;
import com.adobe.printservice.repository.RenderTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

  @Mock
  private JobRepository jobRepository;

  @Mock
  private RenderTemplateRepository templateRepository;

  @InjectMocks
  private JobService jobService;

  @Nested
  @DisplayName("Job Creation (createJob)")
  class CreateJobTests {

    @Test
    void shouldCreateJobWhenTemplateExists() {
      // Arrange
      String templateId = "valid-template";
      Map<String, Object> params = Map.of("key", "value");

      when(templateRepository.existsById(templateId)).thenReturn(true);
      when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      Job job = jobService.createJob(templateId, params);

      // Assert
      assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
      assertThat(job.getAttempts()).isZero();
      assertThat(job.getTemplateId()).isEqualTo(templateId);
      verify(jobRepository).save(job);
    }

    @Test
    void shouldThrowExceptionWhenTemplateDoesNotExist() {
      // Arrange
      when(templateRepository.existsById("invalid")).thenReturn(false);

      // Act & Assert
      assertThatThrownBy(() -> jobService.createJob("invalid", Map.of()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Template not found");

      verify(jobRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("State Machine Processing (processRenderResult)")
  class ProcessRenderResultTests {

    @Test
    void shouldTransitionToDoneOnSuccess() {
      // Arrange
      Job job = new Job();
      job.setId("job-1");
      job.setStatus(JobStatus.PROCESSING);

      when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

      // Act
      RenderResult success = new RenderResult.Success("http://s3.aws.com/doc.pdf");
      jobService.processRenderResult("job-1", success);

      // Assert
      assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
      assertThat(job.getResultContent()).isEqualTo("http://s3.aws.com/doc.pdf");
      verify(jobRepository).save(job);
    }

    @Test
    void shouldRequeueOnTransientFailure() {
      // Arrange
      Job job = new Job();
      job.setId("job-1");
      job.setAttempts(1); // Ya ha fallado una vez
      job.setStatus(JobStatus.PROCESSING);

      when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

      // Act
      RenderResult failure = new RenderResult.Failure("Timeout reaching PDF engine");
      jobService.processRenderResult("job-1", failure);

      // Assert
      assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
      assertThat(job.getAttempts()).isEqualTo(2);
      assertThat(job.getErrorMessage()).isEqualTo("Timeout reaching PDF engine");
      verify(jobRepository).save(job);
    }

    @Test
    void shouldTransitionToFailedOnMaxRetries() {
      // Arrange
      Job job = new Job();
      job.setId("job-1");
      job.setAttempts(2); // Está en su último intento antes de fallar permanentemente (2 + 1 = 3)
      job.setStatus(JobStatus.PROCESSING);

      when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

      // Act
      RenderResult failure = new RenderResult.Failure("Fatal rendering error");
      jobService.processRenderResult("job-1", failure);

      // Assert
      assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
      assertThat(job.getAttempts()).isEqualTo(3);
      assertThat(job.getErrorMessage()).isEqualTo("Fatal rendering error");
      verify(jobRepository).save(job);
    }
  }

  @Nested
  @DisplayName("Batch Locking (fetchAndLockNextBatch)")
  class FetchAndLockNextBatchTests {

    @Test
    void shouldUpdateStatusToProcessingForLockedJobs() {
      // Arrange
      Job job1 = new Job(); job1.setStatus(JobStatus.QUEUED);
      Job job2 = new Job(); job2.setStatus(JobStatus.QUEUED);

      when(jobRepository.findAndLockNextJobs(2)).thenReturn(List.of(job1, job2));

      // Act
      List<Job> lockedJobs = jobService.fetchAndLockNextBatch(2);

      // Assert
      assertThat(lockedJobs).hasSize(2);
      assertThat(job1.getStatus()).isEqualTo(JobStatus.PROCESSING);
      assertThat(job2.getStatus()).isEqualTo(JobStatus.PROCESSING);

      // Verificamos que se hizo el saveAll con la lista modificada
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<Job>> captor = ArgumentCaptor.forClass(List.class);
      verify(jobRepository).saveAll(captor.capture());
      assertThat(captor.getValue()).containsExactlyInAnyOrder(job1, job2);
    }
  }
}