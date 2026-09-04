package com.adobe.printservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Immutable DTO representing the payload for submitting a new print job.
 * Enforces basic structural validation at the controller level.
 *
 * @param templateId The requested template identifier (must not be blank).
 * @param parameters The dynamic payload for the render engine (must not be null).
 */
public record JobSubmitRequest(
    @NotBlank(message = "templateId is required")
    String templateId,

    @NotNull(message = "parameters map cannot be null")
    Map<String, Object> parameters
) {}