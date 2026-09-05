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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Background worker responsible for polling queued jobs and orchestrating their execution.
 * <p>
 * Employs Java 25 Virtual Threads (Project Loom) to process I/O-bound rendering tasks
 * concurrently without exhausting OS threads. It implements a pull-based batching strategy,
 * utilizing fixed delays to provide natural backpressure and prevent database flooding.
 */
@Component
public class JobPollingWorker {

  private static final Logger log = LoggerFactory.getLogger(JobPollingWorker.class);
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
   * Periodically fetches a locked batch of queued jobs and triggers their execution.
   * The fixed delay ensures a new poll cycle only starts after the current batch is fully processed.
   */
  @Scheduled(fixedDelayString = "${worker.poll.interval.ms:2000}")
  public void pollAndProcessJobs() {
    WorkerLogEvent.WORKER_POLL_STARTED.log(log);

    List<Job> lockedJobs = jobService.fetchAndLockNextBatch(BATCH_SIZE);

    if (lockedJobs.isEmpty()) {
      return;
    }

    processBatchConcurrently(lockedJobs);
  }

  /**
   * Processes a batch of jobs concurrently using a thread-per-task model.
   * <p>
   * Leverages an {@link AutoCloseable} executor to enforce structured concurrency.
   * The method inherently blocks at the end of the try-with-resources block until
   * all spawned virtual threads have terminated, ensuring safe sequential database updates.
   *
   * @param jobs The batch of locked jobs ready for processing.
   */
  private void processBatchConcurrently(List<Job> jobs) {
    List<Future<JobExecution>> futures;

    try (var executor = Executors.newThreadPerTaskExecutor(virtualThreadFactory)) {
      futures = jobs.stream()
          .map(job -> {
            RenderTask task = new RenderTask(job.getId(), job.getTemplateId(), job.getParameters());
            return executor.submit(() -> new JobExecution(job.getId(), renderEngine.render(task)));
          })
          .toList();
    }

    for (Future<JobExecution> future : futures) {
      try {
        JobExecution execution = future.resultNow();
        jobService.processRenderResult(execution.jobId(), execution.result());
      } catch (IllegalStateException e) {
        WorkerLogEvent.WORKER_VIRTUAL_THREAD_ERROR.log(log, e.getMessage());
      }
    }
  }

  /**
   * Internal immutable DTO binding a Job ID to its deterministic execution result
   * to safely cross thread boundaries back to the orchestrator.
   */
  private record JobExecution(String jobId, RenderResult result) {}
}