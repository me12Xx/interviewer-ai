# Baseline

- Java: 21
- Spring Boot: 4.1.1
- Build tool: Maven
- Default test profile: offline tests only

The current local baseline includes the 30-case deterministic evaluation and
passes the default test suite. MySQL and external API checks are separate
profiles because they require services or credentials that should not be
needed for a clean checkout.

The application reads database passwords, model keys, reranker keys, and the
administrator token from environment variables. Do not put those values in
source files or commit them to Git.
