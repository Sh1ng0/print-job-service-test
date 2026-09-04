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

    // Construimos la cabecera Location estándar de REST para respuestas 201
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
   * Requirement: GET /jobs - optionally filterable by status
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
   * Requirement: GET /jobs/{id}/result - returns the rendered output.
   * Decision:
   * - If not found -> 404 Not Found
   * - If Job exists but state is QUEUED/PROCESSING -> 409 Conflict (not ready)
   * - If Job exists but state is FAILED -> 409 Conflict (no result will ever exist)
   * - If DONE -> 200 OK with content.
   */
  @GetMapping("/{id}/result")
  public ResponseEntity<String> getJobResult(@PathVariable String id) {
    return jobService.getJob(id).map(job -> {
      return switch (job.getStatus()) {
        case DONE -> ResponseEntity.ok(job.getResultContent());
        case FAILED -> ResponseEntity.status(HttpStatus.CONFLICT)
            .body("Job failed. No result available. Reason: " + job.getErrorMessage());
        case QUEUED, PROCESSING -> ResponseEntity.status(HttpStatus.CONFLICT)
            .body("Job is currently " + job.getStatus() + ". Result not yet available.");
      };
    }).orElseGet(() -> ResponseEntity.notFound().build());
  }
}