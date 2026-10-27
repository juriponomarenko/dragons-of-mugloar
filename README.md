# Dragons of Mugloar

A full‑stack solution to the [Dragons of Mugloar](https://dragonsofmugloar.com/) test task: a Spring Boot backend that talks to the game API and can play a full game autonomously, and a Vue 3 frontend that lets a human play the same game with help from the backend's recommendations.

The original assignment is kept verbatim in [TASK.md](TASK.md).

## Stack

| Layer | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1.2 (Spring Web MVC, RestClient, Bean Validation, Actuator), Maven wrapper, JUnit 5, AssertJ, Mockito |
| Frontend | Vue 3.5, TypeScript, Vite 8, Pinia (state management), Vue Router, Vitest, ESLint + Prettier |

## Repository layout

```
.
├── backend/    Spring Boot application: game API client, strategy engine, REST API, CLI autoplayer
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

### Playing a game from the command line

The `autoplay` profile starts no web server; it plays one or more complete games with the built‑in strategy, prints a summary and exits with a non‑zero code if any game ended below 1000 points:

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=autoplay -Dspring-boot.run.arguments=--mugloar.autoplay.games=3
```

## Running the frontend

```bash
cd frontend
pnpm install
pnpm dev
```

The app is served on <http://localhost:5173> and proxies `/api` calls to the backend on port 8080.

Other useful scripts:

```bash
pnpm test:unit    # Vitest unit tests
pnpm lint         # oxlint + ESLint
pnpm type-check   # vue-tsc
pnpm build        # production build into frontend/dist
```

## Backend design

```
client/    MugloarApiClient (interface) + MugloarRestClient (Spring RestClient) + wire DTOs
domain/    Ad, GameState, ShopItem, Probability, AdDecoder — the game model, free of Spring and HTTP
strategy/  AdEvaluator, ShoppingPolicy, GameStrategy — how to play; pure functions over the domain
service/   GameSession, GameEngine, AutoPlayer, GameService — stateful orchestration per game
web/       GameController, GlobalExceptionHandler, view DTOs — the REST API for the frontend
cli/       AutoplayRunner — the "program that reaches 1000+ points" as a command-line run
config/    Typed, validated @ConfigurationProperties for the API connection and the strategy knobs
```

Each layer depends only on the ones below it. The strategy is plain code with no I/O, which keeps it trivially unit‑testable and lets the REST API reuse it for recommendations.

### How the game is played

Every turn the strategy makes one decision, in this order:

1. **Survive.** If lives are at or below the configured threshold (default 2) and a healing potion is affordable, buy one.
2. **Invest.** If gold exceeds the reserve kept for a potion, buy the next upgrade in the configured order. Upgrades raise the dragon's level, which the server rewards with easier ads.
3. **Earn.** Otherwise rank the ads and solve the best eligible one.

Ads are scored as `successRate × reward − (1 − successRate) × lifeCost`, where the success rate is an empirical mapping of the game's textual labels ("Sure thing" … "Impossible") and the cost of a life grows as lives run out. Ads whose text suggests a reputation hit (stealing, kidnapping, …) are penalised. An ad is only *eligible* when its success rate clears a threshold that tightens with fewer lives; if nothing on the board is eligible the strategy prefers buying a potion (which rolls the board over) and only as a last resort attempts the least dangerous ad.

The game encodes some ads (`encrypted = 1` is Base64, `2` is ROT13). The decoder tries the hinted scheme and verifies the result by checking that the decoded probability is a known label, falling back to the other schemes otherwise, so a renumbered scheme cannot silently break the player.

Everything above is tunable through `mugloar.strategy.*` properties (see `StrategyProperties`); the defaults are what the results below were measured with.

### Error handling and validation

- Path variables are validated against a strict id pattern before anything is forwarded upstream; request bodies are bean‑validated.
- Every failure is returned as an RFC 9457 problem document: `400` invalid input, `404` unknown game, `409` game over, `422` action not possible (expired ad, unaffordable item), `502`/`503` upstream API failure.
- Unaffordable or unknown purchases are refused locally, because the game charges a turn even for a failed purchase.
- The autoplayer tolerates transient upstream errors and expired ads (it refreshes the board and retries) and gives up after three consecutive failures instead of looping forever.
- Upstream HTTP 410 ("Game Over") closes the session even if local bookkeeping disagrees.
- Each game session is guarded by a lock so a manual click and an autoplay request cannot interleave.

### REST API

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/games` | Start a new game |
| `GET` | `/api/games` | List games known to this server |
| `GET` | `/api/games/{id}` | Current state (lives, gold, level, score, turn, purchases) |
| `GET` | `/api/games/{id}/ads` | Decoded ads ranked by the strategy, with success rate, expected value, risk and a recommendation |
| `POST` | `/api/games/{id}/ads/{adId}/solve` | Attempt an ad |
| `GET` | `/api/games/{id}/shop` | Shop items with affordability, purchase count and the strategy's suggestion |
| `POST` | `/api/games/{id}/shop/{itemId}/buy` | Buy an item |
| `POST` | `/api/games/{id}/reputation` | Investigate reputation |
| `POST` | `/api/games/{id}/autoplay/step` | Let the strategy take one action |
| `POST` | `/api/games/{id}/autoplay` | Let the strategy play to the end (optional body `{"maxTurns": n}`) |
| `GET` | `/api/games/{id}/history` | Every action taken in the game, with the strategy's reasoning |

### Tests

`./mvnw test` runs 100+ unit tests: label parsing and ad decoding, scoring and eligibility rules, shopping priorities, the turn loop against an in‑memory fake of the game API (including expired ads, upstream outages and game over), the HTTP client against `MockRestServiceServer`, and the controller with `MockMvc` (status codes, problem documents, validation).

### Results

Measured against the live API with the CLI autoplayer (5 games per configuration, 27 Oct 2026):

| Configuration | Min | Avg | Max | Reached 1000+ |
| --- | --- | --- | --- | --- |
| Potion at ≤2 lives, 50 gold reserve | 1225 | 3099 | 4630 | 5/5 |
| **Potion at ≤3 lives, 100 gold reserve (default)** | 3348 | 4216 | 5259 | 5/5 |
| Default, but each upgrade bought only once | 1957 | 4884 | 6606 | 5/5 |

The more defensive potion policy trades a few turns of upgrades for extra lives and raised the floor by almost 3×, so it became the default. Buying each upgrade only once (then hoarding gold for potions) produces longer games and a higher average, but a lower floor; since the task asks for *reliably* reaching 1000 points, the configuration with the highest minimum is the default and the other is one property away (`mugloar.strategy.rebuy-upgrades=false`). Every configuration tried cleared the 1000‑point bar in every game; the figures are small samples and vary from run to run.

## Frontend

_To be filled in as the frontend lands._

## Use of AI tools

Claude Code was used as a pair‑programming assistant throughout: scaffolding, drafting code and tests, probing the game API, and documentation. All design decisions, the game strategy and the final code were reviewed and are owned by the author.
