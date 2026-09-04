package com.adobe.printservice.domain;



public sealed interface RenderResult {

  /**
   * Represents a successful execution of the rendering task.
   */
  record Success(String resultContent) implements RenderResult {}

  /**
   * Represents a failure during execution (e.g., a simulated network glitch).
   */
  record Failure(String errorMessage) implements RenderResult {}
}