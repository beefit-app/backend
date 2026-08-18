# Repository Guidelines

> Documento compartilhado entre os repositórios `frontend` e `backend`. Quando
> um deles for clonado isoladamente, os caminhos com esses prefixos se referem
> à raiz do respectivo repositório.

## Project Structure

The React/TypeScript/Vite client is in `frontend/`; code is under `src/app/`, styles under `src/styles/`, and native projects under `android/` and `ios/` (evidence: `frontend/package.json` and `vite.config.ts`). The Java 25 Spring Boot API is in `backend/`, with layered packages under `src/main/java/com/beefit/api/` (evidence: `backend/pom.xml` and the package tree).

## Architecture

Before changing system structure or deciding where new code belongs, read
[`ARCHITECTURE.md`](./ARCHITECTURE.md). Consult it before creating modules,
layers, services, repositories, integrations, shared abstractions, or new flows.

- Preserve the documented layer responsibilities, dependency direction, request flows, persistence boundaries, and architectural constraints.
- Do not introduce a different architectural pattern or change an existing boundary without explicit approval. Significant architectural changes require approval before implementation.
- If the code and architecture document disagree, investigate and report the divergence before making an architectural change; do not silently choose either source.
- When an approved task changes architectural decisions or constraints, update `ARCHITECTURE.md` in the same change if documentation is within scope.

## Communication efficiency

Keep progress updates and final reports concise.

Do not repeat the task description or restate information already provided.

Preserve full detail for:

- architectural decisions;
- security implications;
- destructive or irreversible operations;
- unresolved ambiguities;
- validation failures;
- risks and limitations.

Do not shorten code, commands, file paths, error messages or acceptance criteria.

## Commands and Verification

| Instruction | Repository evidence | Automated? | Required scope |
| --- | --- | --- | --- |
| Run Git commands from the subproject. | `frontend/.git/`, `backend/.git/` | Yes—Git selects that history | Git operations only |
| In `frontend/`, use `npm ci` to install and `npm run dev` to serve. Validate changes with `npm run lint`, `npm run test:run`, and `npm run build`. | `package.json`, `package-lock.json`, `eslint.config.js`, `vite.config.ts` | Yes | Frontend only; choose checks matching the change |
| Run `npm run mobile:sync` after web changes that must reach native projects. | `package.json`, `capacitor.config.ts` | Yes | Capacitor work only |
| In `backend/`, use `./mvnw spring-boot:run` to serve and `./mvnw test` to test. For behavior or test changes, run `./mvnw verify`; JaCoCo requires 50% line and 40% branch coverage. | `mvnw`; JaCoCo `check` in `pom.xml` | Yes | Backend only; `verify` for behavior/tests |
| Keep Docker running for PostgreSQL integration tests. | `src/test/java/com/beefit/api/support/PostgreSqlTestSupport.java` | Yes—tests fail without it | Integration tests only |

## Code and Test Conventions

| Instruction | Repository evidence | Automated? | Required scope |
| --- | --- | --- | --- |
| Treat ESLint as the frontend style authority; prefix intentionally unused parameters with `_`. | `frontend/eslint.config.js` | Yes, through lint | Frontend TS/TSX only |
| Name frontend tests `*.test.ts(x)` and backend tests `*Test.java`. | `frontend/src/app/App.test.tsx`, `backend/src/test/java/com/beefit/api/BeefitApiApplicationTests.java`; runner configuration | Yes—runner discovery | New/renamed tests only |

## Commits and Configuration

| Instruction | Repository evidence | Automated? | Required scope |
| --- | --- | --- | --- |
| Use Conventional Commits, such as `fix(api-client): force logout on failed refresh`; use a lowercase imperative subject of at most 72 characters. | Both `README.md` files; recent histories | No hook found | Commits only |
| Do not commit `frontend/.env` or `.env.local`; base settings on `.env.example`. | `frontend/.gitignore`, `.env.example` | Yes—Git ignores both files | Frontend configuration only |
| Supply `JWT_SECRET` when running the backend. | `backend/src/main/resources/application.yaml` | Yes—startup cannot resolve an absent secret | Backend runtime only |
