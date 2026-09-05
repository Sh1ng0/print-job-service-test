package com.adobe.printservice.service;



import com.adobe.printservice.domain.RenderResult;
import com.adobe.printservice.model.Job;
import com.adobe.printservice.model.JobStatus;
import com.adobe.printservice.repository.JobRepository;
import com.adobe.printservice.repository.RenderTemplateRepository;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * Core business service managing the lifecycle and state transitions of Print Jobs.
 * <p>
 * This service acts as a state machine for rendering jobs, handling the progression from
 * {@code QUEUED} to {@code PROCESSING}, and ultimately to {@code DONE} or {@code FAILED}.
 * It relies on database-level locking mechanisms to allow concurrent background workers
 * to fetch and process batches safely without duplication.
 * <p>
 * Responsibilities include:
 * <ul>
 *     <li>Validating and persisting incoming job submissions.</li>
 *     <li>Safely locking batches of queued jobs for concurrent worker execution.</li>
 *     <li>Applying retry policies and state transitions based on immutable render results.</li>
 *     <li>Providing read-only access for API status inquiries.</li>
 * </ul>
 */
@Service
public class JobService {

  private static final Logger log = LoggerFactory.getLogger(JobService.class);
  private static final int MAX_RETRIES = 3;

  private final JobRepository jobRepository;
  private final RenderTemplateRepository templateRepository;

  public JobService(JobRepository jobRepository, RenderTemplateRepository templateRepository) {
    this.jobRepository = jobRepository;
    this.templateRepository = templateRepository;
  }

  /**
   * Validates the template and creates a new job in QUEUED state.
   */
  @Transactional
  public Job createJob(String templateId, Map<String, Object> parameters) {
    if (!templateRepository.existsById(templateId)) {
      JobServiceLogEvent.TEMPLATE_NOT_FOUND.log(log, templateId);
      throw new IllegalArgumentException("Template not found: " + templateId);
    }

    Job job = new Job();
    job.setTemplateId(templateId);
    job.setParameters(parameters);
    job.setStatus(JobStatus.QUEUED);
    job.setAttempts(0);
    job.setCreatedAt(Instant.now());
    job.setUpdatedAt(Instant.now());

    Job savedJob = jobRepository.save(job);
    JobServiceLogEvent.JOB_CREATED.log(log, savedJob.getId());
    return savedJob;
  }

  /**
   * Retrieves a job by its ID for API queries.
   */
  @Transactional(readOnly = true)
  public Optional<Job> getJob(String id) {
    return jobRepository.findById(id);
  }

  /**
   * Fetches a batch of jobs from the database, locking them for processing.
   */
  @Transactional
  public List<Job> fetchAndLockNextBatch(int limit) {
    List<Job> lockedJobs = jobRepository.findAndLockNextJobs(limit);

    if (!lockedJobs.isEmpty()) {
      JobServiceLogEvent.JOBS_LOCKED_FOR_PROCESSING.log(log, lockedJobs.size());
      lockedJobs.forEach(job -> {
        job.setStatus(JobStatus.PROCESSING);
        job.setUpdatedAt(Instant.now());
      });
      jobRepository.saveAll(lockedJobs);
    }

    return lockedJobs;
  }

  /**
   * Transforms the immutable engine result back into JPA mutable state.
   * Uses an exhaustive switch to ensure all flows are handled.
   */
  @Transactional
  public void processRenderResult(String jobId, RenderResult result) {
    Job job = jobRepository.findById(jobId)
        .orElseThrow(() -> new IllegalStateException("Job vanished from DB: " + jobId));

    switch (result) {
      case RenderResult.Success success -> {
        job.setStatus(JobStatus.DONE);
        job.setResultContent(success.resultContent());
        JobServiceLogEvent.JOB_RENDER_SUCCESS.log(log, jobId);
      }
      case RenderResult.Failure failure -> {
        int newAttempts = job.getAttempts() + 1;
        job.setAttempts(newAttempts);
        job.setErrorMessage(failure.errorMessage());

        if (newAttempts >= MAX_RETRIES) {
          job.setStatus(JobStatus.FAILED);
          JobServiceLogEvent.JOB_MAX_RETRIES_EXCEEDED.log(log, jobId, failure.errorMessage());
        } else {
          job.setStatus(JobStatus.QUEUED);
          JobServiceLogEvent.JOB_TRANSIENT_FAILURE.log(log, jobId, newAttempts, MAX_RETRIES, failure.errorMessage());
        }
      }
    }

    job.setUpdatedAt(Instant.now());
    jobRepository.save(job);
  }


  /**
   * Retrieves the list of jobs, optionally filtered by status.
   */
  @Transactional(readOnly = true)
  public List<Job> listJobs(JobStatus status) {
    if (status == null) {
      return jobRepository.findAll();
    }
    return jobRepository.findByStatus(status);
  }

  /**
   * Retrieves the result content if the job is DONE.
   */
  @Transactional(readOnly = true)
  public Optional<String> getJobResult(String id) {
    return jobRepository.findById(id).map(Job::getResultContent);
  }
}