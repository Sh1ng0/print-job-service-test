package com.adobe.printservice.domain;


/**
 * Defines the core contract for the rendering process.
 * Pure functional interface mapping a rendering task to its final result without side effects.
 */
public interface RenderEngine {

  /**
   * Processes a rendering task and returns its deterministic outcome.
   *
   * @param task The immutable context containing the job ID, template, and payload.
   * @return A sealed {@link RenderResult} indicating either a successful render or a transient failure.
   */
  RenderResult render(RenderTask task);
}