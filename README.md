# Print/Render Service - Technical Interview

### Context

We run a rendering system. Clients submit a **render job** (a template id plus some parameters);
the job is processed **asynchronously**, and processing can occasionally fail transiently.
Clients poll for the job's status and fetch the result once it is done.

The set of available templates already exists (`RenderTemplate` / `GET /templates`) - you do not
need to build template management. Your job is to implement the render job lifecycle end to end,
**including how the service is packaged and run**.

### Prerequisites

- **JDK 25** installed locally (the Maven wrapper handles Maven itself, but you need a JDK to run
  `./mvnw` or your IDE).
- **Docker Desktop** (Mac/Windows) or **Docker Engine + the Compose plugin** (Linux), installed
  and running. Everything containerization-related in this exercise (the `Dockerfile` you're
  given, and the `docker-compose.yml` you write) is public/open-source - no account, license, or
  paid service is required. `docker compose version` should print a v2.x version.
- Ports **8080** (the app) and **5432** (Postgres) free on your machine, or be ready to remap them
  in your `docker-compose.yml` if something else is already using them.
- Git, to fork/push your solution.

### Functional Requirements

- **Submit a job**: `POST /jobs`
  - Body: `{ "templateId": "<uuid>", "parameters": { "any": "key-value data" } }`
  - Must return immediately (do not block the HTTP response on the actual rendering work).
  - Reject with `400` if `templateId` does not match an existing template.
  - On success, return `201` with the created job (id, status `QUEUED`, timestamps).

- **Process a job asynchronously**: once queued, a job must move through
  `QUEUED -> PROCESSING -> DONE` or `QUEUED -> PROCESSING -> FAILED`, driven by a background
  worker - not by an incoming HTTP request. "Rendering" can be simulated (e.g. a short delay);
  it does not need to produce a real document.
  - Some renders fail transiently. A failed attempt should be retried a bounded number of times
    before the job is marked `FAILED` with an error reason recorded.

- **Get job status**: `GET /jobs/{id}` - current status, attempt count, error message if failed,
  and whether a result is available.

- **List jobs**: `GET /jobs` - optionally filterable by status, e.g. `GET /jobs?status=FAILED`.

- **Fetch a result**: `GET /jobs/{id}/result` - returns the rendered output once the job is
  `DONE`. Decide yourself what should happen if it's called before the job finishes, or if the
  job failed.

### Required

- Your solution - code, commit messages, and README - must be in English.
- Java 25.
- This repo contains the existing project skeleton (template lookup, project setup, and a starter
  `Job` entity with the fields implied by the API contract above); fork or create a public
  repository with your solution. The `Job` entity does not model retry scheduling/backoff - that's
  part of what you're designing.
- You decide the scope of automated tests - we expect to see some.
  `RenderTemplateResourceTest` shows the MockMvc setup used in this repo, if that's a useful
  starting point.
- **The service must be containerized and runnable via Docker Compose alongside an actual
  PostgreSQL server** - i.e. a `postgres` Docker image running as its own container, not H2 or
  any other in-memory/embedded database. A `Dockerfile` for this service is provided; you need to
  write the `docker-compose.yml` that wires this app together with that `postgres` service (the
  app already reads its DB connection from `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` /
  `DB_PASSWORD`, see `application.properties`). H2 is fine for your own test suite - it must not
  be what the running application connects to.
- The service must expose:
  - A liveness endpoint.
  - A readiness endpoint that reflects more than "the HTTP server is up" - think about what else
    a caller would want to know before considering this service ready to take traffic.
  - Some form of basic metrics (job counts by status is enough; format is up to you).
  - Paths for all three are up to you - just document them in your README so we know where to
    look.
- **Add a short "Design Decisions" section to your README** covering the points listed below
  under "A few things we deliberately left open." A few sentences per point is enough - this is
  the starting point for the design discussion, not a design doc.
- **Once the code is complete, reply to your hiring contact with a link to your repository.**

### A few things we deliberately left open

We're not going to tell you how to implement the queue/worker, the retry policy, or what your
readiness check should verify - that's for you to decide.

### Design Decisions

*   **Concurrency & Queue Strategy:** The queue is implemented using database-level row locking (`SELECT ... FOR UPDATE SKIP LOCKED`). This ensures safe horizontal scaling, allowing multiple application instances to pull disjoint batches of jobs simultaneously without deadlocks or double-processing.
*   **Domain Isolation & Immutability:** The mutable JPA `Job` entity is strictly treated as a persistence container. To protect the rendering logic from database metadata, only the strictly necessary fields are extracted into an immutable `RenderTask` record before passing it to the engine.
*   **Exhaustive State Machine:** The engine's output is modeled as a `RenderResult` sealed interface. This allows the core service to use Java 25 pattern matching (`switch` expressions) to handle state transitions, guaranteeing at compile-time that all success and failure paths are explicitly covered.
*   **Worker Concurrency (Virtual Threads):** I/O-bound rendering tasks are processed concurrently using a thread-per-task model powered by Virtual Threads. Wrapping the executor in a `try-with-resources` block enforces structured concurrency, elegantly blocking the main orchestrator until all virtual threads in the batch terminate.
*   **Scheduling & Backpressure:** The background worker polls the database using a `@Scheduled(fixedDelay=...)` strategy. Since the delay interval only starts after the previous batch is fully processed, it acts as a natural backpressure mechanism, preventing database flooding during traffic spikes.
*   **Structured Observability:** A custom enum-based logging facade was implemented for business events. This guarantees that all state transitions, retries, and errors are logged consistently.

### Observability & Endpoints

The service exposes the following endpoints as required:

**Business API:**
*   `POST /jobs` - Submit a new render job.
*   `GET /jobs` - List all jobs (supports `?status=` filtering).
*   `GET /jobs/{id}` - Get job status and details.
*   `GET /jobs/{id}/result` - Fetch the rendered output (Returns `200 OK` if DONE, `409 Conflict` if processing/failed).

**Actuator (Health & Metrics):**
*   **Liveness Probe:** `GET /actuator/health/liveness` (Confirms the HTTP server is accepting traffic).
*   **Readiness Probe:** `GET /actuator/health/readiness` (Confirms the application is fully ready, automatically verifying the underlying PostgreSQL database connection).
*   **Custom Metrics:** `GET /actuator/metrics/printservice.jobs.count` (Exposes the real-time count of jobs grouped by their `status` tag).


### Optional (not required to complete the exercise)

- Demonstrate that running two instances of your app against the same database does not cause a
  job to be processed twice.
- A Kubernetes `Deployment`/`Service` manifest for this app (it does not need to be applied to a
  real cluster - we're interested in the manifest itself, e.g. how you wire up probes).

### How to run

Building
```shell
$ ./mvnw compile
```

Test
```shell
$ ./mvnw test
```

Start the application (once you've written `docker-compose.yml`)
```shell
$ docker compose up --build
```

Listing available templates
```shell
$ curl localhost:8080/templates
```
