package com.adobe.printservice.web.dto;

import com.adobe.printservice.model.Job;
import com.adobe.printservice.model.JobStatus;
import java.time.Instant;

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