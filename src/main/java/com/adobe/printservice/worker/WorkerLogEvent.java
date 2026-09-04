package com.adobe.printservice.worker;

import com.adobe.printservice.observability.Loggable;

/**
 * Structured log events specific to the polling worker.
 * Covers the wake-up cycle and per‑job rendering start.
 */
public enum WorkerLogEvent implements Loggable {

  WORKER_POLL_STARTED(LogLevel.DEBUG, "Worker waking up to poll for QUEUED jobs"),
  JOB_PROCESSING_STARTED(LogLevel.DEBUG, "Starting render simulation for Job ID: {}");

  private final LogLevel level;
  private final String messageTemplate;

  WorkerLogEvent(LogLevel level, String messageTemplate) {
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