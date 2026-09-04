package com.adobe.printservice.service;

import com.adobe.printservice.observability.Loggable;

/**
 * Structured log events specific to the JobService layer.
 * Covers job creation, locking, rendering results and retry logic.
 */
public enum JobServiceLogEvent implements Loggable {

  // --- API / Creation ---
  JOB_SUBMISSION_RECEIVED(LogLevel.DEBUG, "Received job submission request for templateId: {}"),
  TEMPLATE_NOT_FOUND(LogLevel.WARN, "Job submission rejected. Template ID not found: {}"),
  JOB_CREATED(LogLevel.INFO, "Job successfully created with ID: {} in QUEUED state"),

  // --- Locking (called from worker but executed within service) ---
  JOBS_LOCKED_FOR_PROCESSING(LogLevel.INFO, "Worker successfully locked {} jobs for processing"),

  // --- Rendering result handling ---
  JOB_RENDER_SUCCESS(LogLevel.INFO, "Job ID: {} completed successfully and marked as DONE"),
  JOB_TRANSIENT_FAILURE(LogLevel.WARN, "Transient failure for Job ID: {}. Attempt {}/{} failed. Reason: {}"),
  JOB_MAX_RETRIES_EXCEEDED(LogLevel.ERROR, "Job ID: {} reached maximum retry limit. Marking as FAILED. Last error: {}");

  private final LogLevel level;
  private final String messageTemplate;

  JobServiceLogEvent(LogLevel level, String messageTemplate) {
    this.level = level;
    this.messageTemplate = messageTemplate;
  }

  @Override
  public LogLevel getLevel() {
    return level;
  }

  @Override
  public String getMessageTemplate() {
    return messageTemplate;
  }
}