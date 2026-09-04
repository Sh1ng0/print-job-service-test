package com.adobe.printservice.web;

import com.adobe.printservice.model.Job;
import com.adobe.printservice.service.JobService;
import com.adobe.printservice.web.dto.JobResponse;
import com.adobe.printservice.web.dto.JobSubmitRequest;
import jakarta.validation.Valid;
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
@RequestMapping("/api/jobs")
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
}