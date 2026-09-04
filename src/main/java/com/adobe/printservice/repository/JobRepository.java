package com.adobe.printservice.repository;


import com.adobe.printservice.model.Job;
import com.adobe.printservice.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for managing {@link Job} persistence.
 * Provides standard CRUD operations and custom native queries for concurrent background processing.
 */
@Repository
public interface JobRepository extends JpaRepository<Job, String> {

  /**
   * Fetches a batch of queued jobs and locks them for the current transaction.
   * Uses PostgreSQL's native 'FOR UPDATE SKIP LOCKED' to prevent concurrent workers
   * from processing the same jobs, enabling safe, lock-free polling.
   *
   * @param limit The maximum number of jobs to fetch and lock.
   * @return A list of locked {@link Job} entities ready for processing.
   */
  @Query(value = """
        SELECT * FROM job 
        WHERE status = 'QUEUED' 
        ORDER BY created_at ASC 
        LIMIT :limit 
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
  List<Job> findAndLockNextJobs(@Param("limit") int limit);


  /**
   * Recupera todos los trabajos filtrados por su estado actual.
   * Utilizado para el endpoint GET /jobs?status={status}
   */
  List<Job> findByStatus(JobStatus status);


  /**
   * Cuenta el número total de trabajos en un estado específico.
   * Utilizado para las métricas de Actuator.
   */
  long countByStatus(JobStatus status);
}