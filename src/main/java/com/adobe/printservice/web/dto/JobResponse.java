package com.adobe.printservice.web.dto;

import com.adobe.printservice.model.Job;
import com.adobe.printservice.model.JobStatus;
import java.time.Instant;


/**
 * Immutable DTO representing the outgoing payload for Print Job details.
 * <p>
 * This record unifies the response structure for both job submission and status polling,
 * ensuring strict compliance with the API contract. It intentionally excludes the actual
 * rendered content to keep the payload lightweight, exposing instead a {@code hasResult}
 * flag to indicate readiness.
 *
 * @param id The unique identifier of the job.
 * @param templateId The ID of the template being rendered.
 * @param status The current progression state (e.g., QUEUED, PROCESSING, DONE).
 * @param attempts The number of processing attempts made so far.
 * @param errorMessage The failure reason, if the job encountered transient or fatal errors.
 * @param hasResult A boolean flag indicating if the final rendered output is available for download.
 * @param createdAt The exact timestamp when the job was initially accepted.
 * @param updatedAt The exact timestamp of the last state transition.
 */
public record JobResponse(
    String id,
    String templateId,
    JobStatus status,
    int attempts,
    String errorMessage,
    boolean hasResult,
    Instant createdAt,
    Instant updatedAt
) {
  public static JobResponse fromEntity(Job job) {
    return new JobResponse(
        job.getId(),
        job.getTemplateId(),
        job.getStatus(),
        job.getAttempts(),
        job.getErrorMessage(),
        job.getResultContent() != null,
        job.getCreatedAt(),
        job.getUpdatedAt()
    );
  }
}