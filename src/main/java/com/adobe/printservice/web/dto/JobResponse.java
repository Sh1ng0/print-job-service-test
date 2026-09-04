package com.adobe.printservice.web.dto;

import com.adobe.printservice.model.Job;
import com.adobe.printservice.model.JobStatus;

import java.time.Instant;

/**
 * Immutable DTO for exposing job details to the client.
 * Acts as a shield, hiding internal entity state (e.g., retry counts, technical errors)
 * from the public API contract.
 */
public record JobResponse(
    String id,
    String templateId,
    JobStatus status,
    Instant createdAt,
    Instant updatedAt,
    String resultContent
) {
  /**
   * Factory method to safely map a JPA entity to this immutable DTO.
   */
  public static JobResponse fromEntity(Job job) {
    return new JobResponse(
        job.getId(),
        job.getTemplateId(),
        job.getStatus(),
        job.getCreatedAt(),
        job.getUpdatedAt(),
        job.getResultContent()
    );
  }
}