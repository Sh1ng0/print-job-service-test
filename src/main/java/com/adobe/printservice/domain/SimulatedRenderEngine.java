package com.adobe.printservice.domain;


import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A simulation of the rendering engine for production testing purposes.
 * Introduces artificial delays to simulate heavy processing and randomly
 * triggers transient failures to validate the orchestrator's retry resilience.
 */
@Component
public class SimulatedRenderEngine implements RenderEngine {

  private static final Logger log = LoggerFactory.getLogger(SimulatedRenderEngine.class);

  // 30% chance of throwing a simulated error
  private static final int FAILURE_THRESHOLD = 30;
  private static final int SIMULATED_DELAY_MS = 2500;

  @Override
  public RenderResult render(RenderTask task) {
    log.debug("Starting simulated render process for task: {}", task.id());

    try {
      // Simulating heavy I/O or computational work.
      // In a Virtual Thread environment, this sleep unmounts gracefully without blocking OS threads.
      Thread.sleep(SIMULATED_DELAY_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new RenderResult.Failure("Thread interrupted during render simulation");
    }

    // Roll the dice to simulate a chaotic environment
    int diceRoll = ThreadLocalRandom.current().nextInt(100);
    if (diceRoll < FAILURE_THRESHOLD) {
      return new RenderResult.Failure("Simulated transient network glitch occurred");
    }

    // Success scenario: return a dummy payload
    String dummyContent = String.format(
        "Dummy render output for template %s. Parameters applied: %d",
        task.templateId(),
        task.parameters().size()
    );

    return new RenderResult.Success(dummyContent);
  }
}