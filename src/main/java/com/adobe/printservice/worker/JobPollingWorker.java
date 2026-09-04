package com.adobe.printservice.worker;


import com.adobe.printservice.domain.RenderEngine;
import com.adobe.printservice.domain.RenderResult;
import com.adobe.printservice.domain.RenderTask;
import com.adobe.printservice.model.Job;




import com.adobe.printservice.service.JobService;
import java.util.concurrent.ThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Background worker responsible for polling queued jobs and orchestrating their execution.
 * Utilizes Java Virtual Threads (Project Loom) to process jobs concurrently without exhausting
 * OS threads, ensuring high throughput even when simulating heavy I/O or rendering tasks.
 */
@Component
public class JobPollingWorker {

  private static final Logger log = LoggerFactory.getLogger(JobPollingWorker.class);

  // Adjust batch size depending on expected throughput and database capabilities
  private static final int BATCH_SIZE = 10;

  private final JobService jobService;
  private final RenderEngine renderEngine;
  private final ThreadFactory virtualThreadFactory;

  public JobPollingWorker(JobService jobService, RenderEngine renderEngine, ThreadFactory virtualThreadFactory) {
    this.jobService = jobService;
    this.renderEngine = renderEngine;
    this.virtualThreadFactory = virtualThreadFactory;
  }

  /**
   * Polls the database at fixed intervals.
   * The fixed delay starts counting after the previous execution finishes,
   * preventing overlapping poll cycles.
   */
  @Scheduled(fixedDelayString = "${worker.poll.interval.ms:2000}")
  public void pollAndProcessJobs() {
    WorkerLogEvent.WORKER_POLL_STARTED.log(log);

    List<Job> lockedJobs = jobService.fetchAndLockNextBatch(BATCH_SIZE);

    if (lockedJobs.isEmpty()) {
      return; // Nothing to do, sleep until next cycle
    }

    processBatchConcurrently(lockedJobs);
  }

  /**
   * Processes a batch of jobs using a Virtual Thread per task.
   * Employs standard AutoCloseable Executors to achieve structured-like concurrency
   * without relying on JDK preview features.
   */
  private void processBatchConcurrently(List<Job> jobs) {
    List<Future<JobExecution>> futures;

    // The try-with-resources block implicitly calls executor.close(),
    // which completely blocks the current thread until ALL submitted virtual threads terminate.
    try (var executor = Executors.newThreadPerTaskExecutor(virtualThreadFactory)) {

      futures = jobs.stream()
          .map(job -> {
            RenderTask task = new RenderTask(job.getId(), job.getTemplateId(), job.getParameters());
            return executor.submit(() -> new JobExecution(job.getId(), renderEngine.render(task)));
          })
          .toList();

    } // <-- The orchestrator waits here automatically. Zero callbacks.

    // At this point, all virtual threads have finished.
    // We can safely process the results sequentially and update the database.
    for (Future<JobExecution> future : futures) {
      try {
        JobExecution execution = future.resultNow(); // Safe to call since executor is closed
        jobService.processRenderResult(execution.jobId(), execution.result());
      } catch (IllegalStateException e) {
        log.error("Failed to retrieve result from virtual thread. Task might have been cancelled.", e);
      }
    }
  }

  /**
   * Internal record to bind a Job ID to its deterministic RenderResult
   * across thread boundaries.
   */
  private record JobExecution(String jobId, RenderResult result) {}
}