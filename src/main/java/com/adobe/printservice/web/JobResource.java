package com.adobe.printservice.web;

import com.adobe.printservice.model.Job;
import com.adobe.printservice.model.JobStatus;
import com.adobe.printservice.service.JobService;
import com.adobe.printservice.web.dto.JobResponse;
import com.adobe.printservice.web.dto.JobSubmitRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * REST Controller exposing the Print Job API.
 * Handles HTTP semantics, request validation, and payload mapping,
 * delegating all business logic to the underlying JobService.
 */
@RestController
@RequestMapping("/jobs")
public class JobResource {

  private final JobService jobService;

  public JobResource(JobService jobService) {
    this.jobService = jobService;
  }

  /**
   * Submits a new print job for asynchronous processing.
   *
   * @param request The validated job submission payload.
   * @return 201 Created with the location of the new job and its initial state.
   */
  @PostMapping
  public ResponseEntity<JobResponse> submitJob(@Valid @RequestBody JobSubmitRequest request) {
    Job createdJob = jobService.createJob(request.templateId(), request.parameters());
    JobResponse response = JobResponse.fromEntity(createdJob);

    URI location = ServletUriComponentsBuilder
        .fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(response.id())
        .toUri();

    return ResponseEntity.created(location).body(response);
  }

  /**
   * Retrieves the current status and details of a specific print job.
   *
   * @param id The unique identifier of the job.
   * @return 200 OK with the job details, or 404 Not Found.
   */
  @GetMapping("/{id}")
  public ResponseEntity<JobResponse> getJob(@PathVariable String id) {
    return jobService.getJob(id)
        .map(JobResponse::fromEntity)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * Retrieves a list of jobs, optionally filtered by their current status.
   *
   * @param status (Optional) The {@link JobStatus} to filter the results by.
   * @return 200 OK with a list of matching job details.
   */
  @GetMapping
  public ResponseEntity<List<JobResponse>> listJobs(@RequestParam(required = false) JobStatus status) {
    List<JobResponse> responses = jobService.listJobs(status)
        .stream()
        .map(JobResponse::fromEntity)
        .toList();
    return ResponseEntity.ok(responses);
  }

  /**
   * Fetches the rendered output of a completed job.
   * <p>
   * API Design Decisions:
   * <ul>
   *     <li>Returns 200 OK with the content if the job is DONE.</li>
   *     <li>Returns 409 Conflict if the job is QUEUED or PROCESSING (resource not yet ready).</li>
   *     <li>Returns 409 Conflict if the job FAILED (resource will never be available).</li>
   *     <li>Returns 404 Not Found if the job ID does not exist.</li>
   * </ul>
   *
   * @param id The unique identifier of the job.
   * @return The rendered content, or an appropriate error status.
   */
  @GetMapping("/{id}/result")
  public ResponseEntity<String> getJobResult(@PathVariable String id) {
    return jobService.getJob(id)
        .map(job -> switch (job.getStatus()) {
          case DONE -> ResponseEntity.ok(job.getResultContent());
          case FAILED -> ResponseEntity.status(HttpStatus.CONFLICT)
              .body("Job failed. No result available. Reason: " + job.getErrorMessage());
          case QUEUED, PROCESSING -> ResponseEntity.status(HttpStatus.CONFLICT)
              .body("Job is currently " + job.getStatus() + ". Result not yet available.");
        })
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}