# Dragons of Mugloar

A full‑stack solution to the [Dragons of Mugloar](https://dragonsofmugloar.com/) test task: a Spring Boot backend that talks to the game API and can play a full game autonomously, and a Vue 3 frontend that lets a human play the same game with help from the backend's recommendations.

The original assignment is kept verbatim in [TASK.md](TASK.md).

## Stack

| Layer | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1.2 (Spring Web, Bean Validation, Actuator), Maven wrapper, JUnit 5 |
| Frontend | Vue 3.5, TypeScript, Vite 8, Pinia (state management), Vue Router, Vitest, ESLint + Prettier |

## Repository layout

```
.
├── backend/    Spring Boot application (REST API + game-playing engine)
├── frontend/   Vue 3 single-page application
├── TASK.md     The assignment text and API summary
└── README.md
```

## Prerequisites

- JDK 21 (e.g. [Eclipse Temurin](https://adoptium.net/))
- Node.js 24+ and [pnpm](https://pnpm.io/) 10+
- No global Maven needed: the project ships the Maven wrapper (`mvnw`).

## Running the backend

```bash
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

The API is served on <http://localhost:8080>. Health check: <http://localhost:8080/actuator/health>.

Run the tests:

```bash
cd backend
./mvnw test
```

## Running the frontend

```bash
cd frontend
pnpm install
pnpm dev
```

The app is served on <http://localhost:5173> and proxies API calls to the backend on port 8080.

Other useful scripts:

```bash
pnpm test:unit    # Vitest unit tests
pnpm lint         # oxlint + ESLint
pnpm type-check   # vue-tsc
pnpm build        # production build into frontend/dist
```

## How it works

_To be filled in as the implementation lands: game-playing strategy, backend API, frontend state model, decision-support tools._

## Use of AI tools

Claude Code was used as a pair‑programming assistant throughout: scaffolding, drafting code and tests, and documentation. All design decisions, the game strategy and the final code were reviewed and are owned by the author.
