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
@DisplayName("Job Service Unit Tests")
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
    @DisplayName("Successfully creates a QUEUED job when the template exists")
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
    @DisplayName("Throws IllegalArgumentException when the template does not exist")
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
    @DisplayName("Transitions job to DONE and saves content on successful render")
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
    @DisplayName("Re-queues job and increments attempts on transient failure")
    void shouldRequeueOnTransientFailure() {
      // Arrange
      Job job = new Job();
      job.setId("job-1");
      job.setAttempts(1);
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
    @DisplayName("Transitions job to FAILED when max retries are exceeded")
    void shouldTransitionToFailedOnMaxRetries() {
      // Arrange
      Job job = new Job();
      job.setId("job-1");
      job.setAttempts(2); // 2 previous attempts + this 1 = 3 (MAX)
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

    @Test
    @DisplayName("Throws IllegalStateException if the job is deleted before result is processed")
    void shouldThrowExceptionWhenJobVanishes() {
      // Arrange
      when(jobRepository.findById("ghost-job")).thenReturn(Optional.empty());
      RenderResult success = new RenderResult.Success("content");

      // Act & Assert
      assertThatThrownBy(() -> jobService.processRenderResult("ghost-job", success))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Job vanished from DB: ghost-job");

      verify(jobRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("Batch Locking (fetchAndLockNextBatch)")
  class FetchAndLockNextBatchTests {

    @Test
    @DisplayName("Updates status to PROCESSING for all locked jobs in the batch")
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

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<Job>> captor = ArgumentCaptor.forClass(List.class);
      verify(jobRepository).saveAll(captor.capture());
      assertThat(captor.getValue()).containsExactlyInAnyOrder(job1, job2);
    }
  }
}