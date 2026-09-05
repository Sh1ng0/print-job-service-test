package com.adobe.printservice.repository;

import com.adobe.printservice.model.Job;
import com.adobe.printservice.service.JobService;
import com.adobe.printservice.support.BaseIntegrationTest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Job Repository Concurrency Tests")
public class JobRepositoryConcurrencyTest extends BaseIntegrationTest {

  private static final Logger log = LoggerFactory.getLogger(JobRepositoryConcurrencyTest.class);

  @Autowired
  private JobRepository jobRepository;

  @Autowired
  private JobService jobService;

  @BeforeEach
  void setUp() {
    jobRepository.deleteAll();
    jobRepository.flush();
  }

  @Test
  @DisplayName("Two concurrent workers should lock disjoint batches without duplicates or deadlocks")
  void shouldHandleConcurrentBatchLockingWithoutDeadlocksOrDuplicates() throws ExecutionException, InterruptedException {
    jobRepository.deleteAll();
    jobRepository.flush();

    List<Job> jobs = IntStream.range(0, 5)
        .mapToObj(this::createTestJob)
        .toList();
    jobRepository.saveAll(jobs);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CompletableFuture<List<Job>> worker1 = CompletableFuture.supplyAsync(() ->
        jobService.fetchAndLockNextBatch(3), executor);
    CompletableFuture<List<Job>> worker2 = CompletableFuture.supplyAsync(() ->
        jobService.fetchAndLockNextBatch(3), executor);

    CompletableFuture.allOf(worker1, worker2).join();
    executor.shutdown();

    List<Job> batch1 = worker1.get();
    List<Job> batch2 = worker2.get();

    // Logs maintained to see the concurrent workers/instances in action
    log.info("Batch1 size: {}, IDs: {}", batch1.size(), batch1.stream().map(Job::getId).toList());
    log.info("Batch2 size: {}, IDs: {}", batch2.size(), batch2.stream().map(Job::getId).toList());

    int totalLocked = batch1.size() + batch2.size();
    assertThat(totalLocked).isEqualTo(5);
    assertThat(batch1).doesNotContainAnyElementsOf(batch2);
  }

  /**
   * Helper method to hide the JPA setter boilerplate during testing.
   */
  private Job createTestJob(int index) {
    Job job = new Job();
    job.setTemplateId("template-" + index);
    job.setParameters(Map.of("key", "value-" + index));
    return job;
  }
}