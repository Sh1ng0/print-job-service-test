package com.adobe.printservice.domain;



import java.util.Map;

/**
 * Immutable representation of a rendering task.
 * This record isolates the core processing logic from the underlying JPA entity,
 * ensuring thread-safety and preventing unintended mutations during asynchronous execution.
 *
 * @param id         The unique identifier of the job.
 * @param templateId The identifier of the template to be applied.
 * @param parameters The read-only payload containing the data to render.
 */
public record RenderTask(
    String id,
    String templateId,
    Map<String, Object> parameters
) {
  /**
   * Compact constructor to ensure the parameters map is strictly immutable.
   */
  public RenderTask {
    parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
  }
}