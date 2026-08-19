# Teddy Repository Instructions

## Project Scope

- Teddy is a lightweight control plane for Spark applications running on YARN. It uploads and manages JARs, submits jobs, records metadata in MySQL, polls YARN state, sends alerts, and optionally restarts failed applications.
- This repository does not contain the business logic of the managed Spark streaming jobs. Do not add job-specific processing code here unless the user explicitly changes the project scope.
- Treat the current application as a small Spring Boot monolith with server-rendered static assets; do not introduce a separate frontend toolchain or distributed architecture without an explicit requirement.

## Technology Baseline

- Backend: Java, Maven, Spring Boot 2.3.5, Spark Launcher 3.2.1, MyBatis, and MySQL.
- Frontend: static HTML, jQuery 1.8.3, and Bootstrap; there is no Node.js build.
- Development is commonly performed on Windows. Runtime packaging and the scripts under `bin/` target Linux.
- Preserve LF line endings and executable semantics for `bin/*.sh`.
- Do not upgrade Java, Spring Boot, Spark, MyBatis, frontend libraries, or add production dependencies unless the task explicitly includes that change.

## Important Paths

- `src/main/java/com/dbay/teddy/Application.java`: application entry point and external Teddy property loading.
- `src/main/java/com/dbay/teddy/controller/`: HTTP endpoints for jobs, resources, and login.
- `src/main/java/com/dbay/teddy/service/JobService.java`: Spark submission, stop, restart, and job persistence orchestration.
- `src/main/java/com/dbay/teddy/service/YarnService.java`: YARN ResourceManager REST access.
- `src/main/java/com/dbay/teddy/manager/`: state refresh, restart, alert, resource, token, email, and webhook behavior.
- `src/main/java/com/dbay/teddy/mapper/JobMapper.java`: MySQL `job` table schema and queries.
- `src/main/resources/static/`: browser UI and AJAX calls.
- `src/main/resources/config/application.properties`: Spring Boot and datasource settings.
- `conf/teddy.properties`: deployment-specific Spark, YARN, resource, log, alert, and restart settings.
- `.agents/memory.md`: local machine and collaboration memory. Keep local-only facts here and do not stage or commit it unless explicitly requested.

## Change Rules

- Make the smallest change that satisfies the request and preserve the existing controller-service-manager-mapper structure.
- Before changing an endpoint, trace both its backend implementation and every static JavaScript caller.
- Preserve the API envelope `{"state": "success|error", "data": ...}` unless the user requests an API migration.
- Treat the semicolon-delimited Spark configuration format (`key=value;key=value`) as a compatibility boundary unless changing it is part of the task.
- When changing the `Job` model or SQL, update the entity, mapper statements, table-creation SQL, service behavior, and affected UI together.
- Do not silently change job-state semantics. Distinguish transitional YARN states from terminal states, and consider how manual stop, alerting, and automatic restart interact.
- Avoid unrelated modernization or formatting changes in the same patch.

## Security and Production Boundaries

- Treat any configured HTTP endpoint, YARN cluster, MySQL database, mail server, webhook, JAR directory, and deployed Teddy instance as production unless the user explicitly identifies a test environment.
- Do not start, stop, submit, kill, restart, delete, upload, or reconfigure a live application or resource without explicit authorization for that operation.
- Do not connect to the configured MySQL, YARN, mail, webhook, or public Teddy endpoint merely to validate a code change. Prefer mocks, local fixtures, and isolated tests.
- Never add, expose, repeat, or commit passwords, tokens, webhook keys, private hosts, or other credentials. When touching existing configuration, replace secrets with placeholders or externalized values and clearly report any migration requirement.
- Keep all resource upload and deletion operations confined to the canonical `lib.home` directory. Reject traversal, absolute-path escape, and unexpected file types.
- Authentication and authorization must be enforced by the backend. A browser-side token check alone is not a security boundary.
- Do not log credentials, full webhook URLs, cookies, or other authentication material.

## Runtime and Domain Invariants

- Spark applications are submitted through the locally installed Spark distribution and normally target YARN in cluster deploy mode.
- A successful submission must obtain an ApplicationId and leave a consistent database record. Handle partial failure so that an application is not silently left running and untracked.
- Stopping an application uses the local YARN CLI. Any change to stop or restart logic must account for automatic restart settings.
- YARN REST calls must be bounded by connection/read timeouts and should avoid duplicate queries for the same application during one refresh cycle.
- Alerting and automatic restart must not treat every non-`RUNNING` state as equivalent. Changes in this area require explicit handling of transitional, successful terminal, failed terminal, killed, and unknown states.
- Preserve compatibility with existing rows in the MySQL `job` table unless a migration is supplied.

## Verification

- For backend changes, run the narrowest relevant unit tests first. Use `mvn -DskipTests=false test` only when the tests do not require live infrastructure.
- For packaging or compilation checks, use `mvn -DskipTests package`. Report when dependency resolution or environment-specific configuration prevents verification.
- Existing tests are sparse and environment-coupled. Add focused tests for changed logic when practical; do not claim coverage from a context-load test alone.
- For static UI changes, verify the corresponding endpoint contract and exercise the affected page locally when a runnable local environment is available. There is no frontend build command.
- Never use the real cluster, database, webhook, or JAR store as a test fixture without explicit user approval.
- In the handoff, state what was changed, what was verified, and any production configuration or migration the user must perform.
