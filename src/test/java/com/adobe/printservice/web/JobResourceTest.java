package com.adobe.printservice.web;

import com.adobe.printservice.model.Job;
import com.adobe.printservice.model.JobStatus;
import com.adobe.printservice.service.JobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;


import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobResource.class)
class JobResourceTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private JobService jobService;

  @Test
  @DisplayName("POST /jobs - Success returns 201 Created with Location header")
  void submitJob_Returns201AndLocation() throws Exception {
    // Arrange
    Job mockJob = createTestJob("job-123", "template-x", JobStatus.QUEUED);
    when(jobService.createJob(eq("template-x"), any())).thenReturn(mockJob);

    String payload = """
                {
                    "templateId": "template-x",
                    "parameters": { "user": "test" }
                }
                """;

    // Act & Assert
    mockMvc.perform(post("/jobs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isCreated())
        .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/jobs/job-123"))
        .andExpect(jsonPath("$.id").value("job-123"))
        .andExpect(jsonPath("$.status").value("QUEUED"));
  }

  @Test
  @DisplayName("POST /jobs - Missing templateId triggers GlobalExceptionHandler and returns 400")
  void submitJob_MissingTemplateId_Returns400() throws Exception {
    // Arrange
    String invalidPayload = """
                {
                    "parameters": { "user": "test" }
                }
                """;

    // Act & Assert
    mockMvc.perform(post("/jobs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidPayload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Validation Error"))
        .andExpect(jsonPath("$.message").value("templateId is required"));
  }

  @Test
  @DisplayName("GET /jobs/{id} - Returns 404 when job does not exist")
  void getJob_NotFound_Returns404() throws Exception {
    // Arrange
    when(jobService.getJob("unknown-id")).thenReturn(Optional.empty());

    // Act & Assert
    mockMvc.perform(get("/jobs/unknown-id"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("GET /jobs/{id}/result - Returns 409 Conflict when job is still PROCESSING")
  void getJobResult_WhenProcessing_Returns409() throws Exception {
    // Arrange
    Job mockJob = createTestJob("job-123", "template-x", JobStatus.PROCESSING);
    when(jobService.getJob("job-123")).thenReturn(Optional.of(mockJob));

    // Act & Assert
    mockMvc.perform(get("/jobs/job-123/result"))
        .andExpect(status().isConflict())
        .andExpect(content().string("Job is currently PROCESSING. Result not yet available."));
  }

  @Test
  @DisplayName("GET /jobs/{id}/result - Returns 200 OK and content when DONE")
  void getJobResult_WhenDone_Returns200() throws Exception {
    // Arrange
    Job mockJob = createTestJob("job-123", "template-x", JobStatus.DONE);
    mockJob.setResultContent("https://s3.aws.com/result.pdf");
    when(jobService.getJob("job-123")).thenReturn(Optional.of(mockJob));

    // Act & Assert
    mockMvc.perform(get("/jobs/job-123/result"))
        .andExpect(status().isOk())
        .andExpect(content().string("https://s3.aws.com/result.pdf"));
  }

  /**
   * Helper method to instantiate Job without boilerplate
   */
  private Job createTestJob(String id, String templateId, JobStatus status) {
    Job job = new Job();
    job.setId(id);
    job.setTemplateId(templateId);
    job.setStatus(status);
    return job;
  }
}