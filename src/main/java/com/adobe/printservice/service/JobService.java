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
   * Valida la plantilla y crea un nuevo trabajo en estado QUEUED.
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
   * Recupera un trabajo por su ID para las consultas de la API.
   */
  @Transactional(readOnly = true)
  public Optional<Job> getJob(String id) {
    return jobRepository.findById(id);
  }

  /**
   * Rescata un lote de trabajos de la base de datos bloqueándolos para su procesamiento.
   */
  @Transactional
  public List<Job> fetchAndLockNextBatch(int limit) {
    List<Job> lockedJobs = jobRepository.findAndLockNextJobs(limit);

    if (!lockedJobs.isEmpty()) {
      JobServiceLogEvent.JOBS_LOCKED_FOR_PROCESSING.log(log, lockedJobs.size());
      // Los marcamos inmediatamente como PROCESSING para reflejar la realidad
      // y que queden excluidos de futuras consultas
      lockedJobs.forEach(job -> {
        job.setStatus(JobStatus.PROCESSING);
        job.setUpdatedAt(Instant.now());
      });
      jobRepository.saveAll(lockedJobs);
    }

    return lockedJobs;
  }

  /**
   * Transforma el resultado inmutable del motor de vuelta al estado mutable de JPA.
   * Utiliza un switch exhaustivo para garantizar que se manejan todos los flujos.
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
          // Lo devolvemos a la cola para que el Worker lo vuelva a atrapar
          job.setStatus(JobStatus.QUEUED);
          JobServiceLogEvent.JOB_TRANSIENT_FAILURE.log(log, jobId, newAttempts, MAX_RETRIES, failure.errorMessage());
        }
      }
    }

    job.setUpdatedAt(Instant.now());
    jobRepository.save(job);
  }
}