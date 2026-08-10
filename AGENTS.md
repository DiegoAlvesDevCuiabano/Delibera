# Repository Guidelines

## Project Structure & Module Organization

Delibera is a Java 21/Spring Boot 3.4 application. Backend code lives in `src/main/java/com/delibera`, organized by `controller`, `service`, `repository`, `model`, `config`, and `exception`. Thymeleaf views are in `src/main/resources/templates`; shared fragments belong in `templates/fragments`, while CSS and JavaScript live under `src/main/resources/static`. Runtime profiles are defined by `application-*.properties`. Tests mirror production packages in `src/test/java`. `Design/` contains static visual prototypes; update the relevant prototype before making substantial UI changes. Operational notes live in `docs/`.

## Build, Test, and Development Commands

- `./mvnw test` (Windows: `.\mvnw.cmd test`) runs all JUnit tests.
- `./mvnw clean verify` performs the CI build and packages the application.
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=h2` starts a disposable H2 environment.
- `docker compose up --build` starts PostgreSQL and the application locally.

Use JDK 21. Never commit generated `target/`, local uploads, secrets, or `.env` files.

## Coding Style & Naming Conventions

Use four-space indentation and constructor injection. Java types use `PascalCase`; methods and fields use `camelCase`; constants use `UPPER_SNAKE_CASE`. Keep controllers focused on HTTP concerns and business rules in services. Apply `@Transactional` in the service layer. Entities use Lombok accessors; public payloads use DTOs. Reuse Thymeleaf fragments and CSS variables instead of duplicating markup or colors. Run `git diff --check` before handoff.

## Testing Guidelines

Tests use JUnit 5, Mockito, Spring Boot Test, MockMvc, and H2. Name test classes `*Test` and methods as `acao_cenario_resultado`. Add unit coverage for service rules and integration/security coverage for route behavior, ownership, validation, and CSRF. Run the focused class first, then the complete suite.

## Commit & Pull Request Guidelines

History follows Conventional Commits in Portuguese: `feat:`, `fix:`, `refactor:`, `chore:`, and `docs:`. Keep commits atomic and do not add AI attribution. Pull requests should explain the user-visible change, list validation commands, link the issue when applicable, and include screenshots for UI changes. Highlight schema, security, profile, or environment-variable changes explicitly.

## Security & Configuration

Preserve tenant filtering on every institution-scoped query. Keep credentials in environment variables and use `application-prod.properties` only with `SPRING_PROFILES_ACTIVE=prod`. Treat student data and uploaded files as personal data subject to the retention rules in `docs/TODO.md`.
