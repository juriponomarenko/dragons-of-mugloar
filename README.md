# Dragons of Mugloar

A full‑stack solution to the [Dragons of Mugloar](https://dragonsofmugloar.com/) test task: a Spring Boot backend that talks to the game API and can play a full game autonomously, and a Vue 3 frontend that lets a human play the same game with help from the backend's recommendations.

The original assignment is kept verbatim in [TASK.md](TASK.md).

## Stack

| Layer | Technology |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1.2 (Spring Web MVC, RestClient, Bean Validation, Actuator), Maven wrapper, JUnit 5, AssertJ, Mockito |
| Frontend | Vue 3.5, TypeScript, Vite 8, Pinia (state management), Vue Router, Vitest, ESLint + Prettier |

## Repository layout

```
.
├── backend/    Spring Boot application: game API client, strategy engine, REST API, CLI autoplayer
├── frontend/   Vue 3 single-page application
├── TASK.md     The assignment text and API summary
└── README.md
```

## Quick start

Two terminals:

```bash
cd backend && ./mvnw spring-boot:run          # API on http://localhost:8080
```

```bash
cd frontend && pnpm install && pnpm dev       # UI on http://localhost:5173
```

Open <http://localhost:5173>, press **Start a new game**, and either pick ads yourself or press **Autoplay**.

## Prerequisites

- JDK 25 (e.g. [Eclipse Temurin](https://adoptium.net/)); `JAVA_HOME` must point to it for the Maven wrapper. The code uses nothing beyond Java 21, and Spring Boot 4.1 supports Java 17 to 27, so an older LTS works with `java.version` lowered in `backend/pom.xml`.
- Node.js 22.18+ or 24+ (tested on 24 and 26) and [pnpm](https://pnpm.io/) 10+
- No global Maven needed: the project ships the Maven wrapper (`mvnw`, `mvnw.cmd` on Windows).

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

The `autoplay` profile starts no web server; it plays one or more games with the built‑in strategy, prints a summary and exits with a non‑zero code if any game ended below 1000 points. A well‑upgraded dragon can go on for hundreds of turns, so each game stops after `mugloar.strategy.max-turns` actions (default 1000, the same cap the UI's *Finish game* uses); the server keeps the score either way:

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

1. **Survive.** If lives are at or below the configured threshold (default 3) and a healing potion is affordable, buy one.
2. **Invest.** Climb the upgrade ladder: rungs are the configured upgrade order, and the next rung is the one bought the fewest times so far, so every upgrade is bought once before any is bought twice. The first rung the gold above the potion reserve covers is bought; an unaffordable rung is skipped for now rather than saved for, because a cheap level-up today beats an expensive one in ten turns. Upgrades raise the dragon's level, which the server rewards with easier ads.
3. **Earn.** Otherwise rank the ads and solve the best eligible one.

Ads are scored as `successRate × reward − (1 − successRate) × lifeCost`, where a life is valued at 100 gold (the potion plus the turn it takes to buy it back), the success rate is an assumed mapping of the game's textual labels ("Sure thing" = 1.0 … "Impossible" = 0; the game never exposes numbers, and these are not rates measured by this project) and the cost of a life grows as lives run out. Ads whose text suggests a reputation hit (stealing, kidnapping, …) have their expected gold discounted before the life cost is subtracted, so the penalty can only ever lower a score. An ad is only *eligible* when its success rate clears a threshold that tightens with fewer lives (60% at three or more lives, so a *Gamble* is never taken at full health; 80% at one life). A life that cannot be replaced counts for one more: when a healing potion is not affordable, ads are judged as if one life fewer remained. If nothing on the board is eligible the strategy prefers buying a potion (which rolls the board over) and only as a last resort attempts the least dangerous ad.

**Labels can lie.** Dumping the history of games that died early showed the same pattern every time: from about turn 25 the board offers "Steal super awesome diamond …" ads labelled *Sure thing* with rewards of 300+ gold, and they fail every single time (nine of nine in the game below). Discounting the reward for the word "steal" is not enough, because 60% of 322 still beats every honest ad. Two rules handle this. Ads matching a configured trap phrase (default `super awesome diamond`) are estimated at 0% whatever the label says. And the player learns within a game: ads are grouped into families by the kind of task they are (the first words of the text with names and places skipped, so "Escort Majda Strange to thrift shoppe" and "Escort Bob Hope to meadow" are one family) together with their label, and once a family has failed twice (configurable) it is treated as a certain failure for the rest of that game, so an unknown trap costs at most two lives. The label is part of the key so that two failed *Hmmm* ads never discredit the *Piece of cake* ads of the same kind. Both show up as notes on the ad in the UI.

```text
 25 XX  L3 g 192 lv  6 sc  892 | Steal super awesome diamond pan from Kassiopeia Clemens | Best eligible ad: Sure thing, reward 228
 26 buy L4 g 142 lv  6 sc  892 | Healing potion                                           | Lives are low (3), buying a healing potion
 30 XX  L3 g 164 lv  8 sc 1114 | Steal super awesome diamond turnips from Cosma Isaacson  | Best eligible ad: Sure thing, reward 280
 34 XX  L3 g  64 lv  8 sc 1114 | Steal super awesome diamond horse from Sonja Courtier    | Best eligible ad: Sure thing, reward 322
 ...
 48 XX  L0 g  17 lv  8 sc 1317 | Steal super awesome diamond horse from Yahui Rowbottom   | Best eligible ad: Sure thing, reward 329
```

The game encodes some ads (`encrypted = 1` is Base64, `2` is ROT13). The decoder tries the hinted scheme and verifies the result by checking that the decoded probability is a known label, falling back to the other schemes otherwise, so a renumbered scheme cannot silently break the player.

Everything above is tunable through `mugloar.strategy.*` properties (see `StrategyProperties`); the defaults are what the results below were measured with.

### Error handling and validation

- Path variables are validated against a strict id pattern before anything is forwarded upstream; request bodies are bean‑validated.
- Every failure is returned as an RFC 9457 problem document: `400` invalid input, `404` unknown game, `409` game over, `422` action not possible (expired ad, unaffordable item, nothing left to do on an empty board), `502`/`503` upstream API failure.
- Unaffordable or unknown purchases are refused locally, because the game charges a turn even for a failed purchase.
- The autoplayer tolerates transient upstream errors and expired ads: it refreshes the board and retries, and gives up after three consecutive failures instead of looping forever.
- An action the API never answered (timeout, dropped connection) may still have been carried out, and a purchase is not safe to repeat. The game API has no endpoint that reports the state, so the session marks it as uncertain and the engine refuses every purchase, manual or automated, until the next answer has refreshed gold and lives. An unanswered purchase is kept as pending and settled by that answer's gold: either it is exactly what the one action explains, so the purchase never happened, or it is not, so it did and is counted, and the upgrade ladder moves on instead of buying it twice. A lost solve answer is harmless, since the API rejects the same ad a second time and the player moves on. The UI treats such outcomes the same way and reloads state and history.
- A rate limit is treated differently. A strong dragon makes around ten requests a second and the API answers HTTP 429 after roughly six hundred of them (about turn 300), so the player waits it out rather than counting it as a failure: 15, 30, 45 and then 60 seconds at a time, for up to six waits in a row. The CLI waits out a rate limit on game start too, and reports a game it could not start rather than aborting the run.
- Upstream HTTP 410 ("Game Over") closes the session even if local bookkeeping disagrees, and so does a 404, since the game server forgets a game after a while of inactivity (a bad ad id is a 400 and a bad item a 200, so a 404 is never about the request). An autoplay run that hits either ends cleanly with that reason, and the UI shows the final state.
- Each action on a game runs under that game's lock, so a manual click and an autoplay request cannot interleave. Reads never wait for it: state, purchases and failed ad families are published together as one immutable snapshot that is swapped atomically (so a response never mixes two turns), and the history and the board are snapshots too, so the UI can follow a game the server is playing.
- The CLI's exit code is non‑zero when any game missed the target or could not be started, and `main` applies it when the context closes, so the run works as a regression check in a shell or CI.

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
| `GET` | `/api/games/{id}/history` | Every action taken in the game, with the strategy's reasoning and the state and purchases as they stood after it |

### Tests

`./mvnw test` runs 130+ unit tests: label parsing and ad decoding, scoring and eligibility rules, shopping priorities, the shipping strategy defaults bound by Spring Boot (so a drifting default fails a test), the CLI runner's exit code, the turn loop against an in‑memory fake of the game API (including expired ads, upstream outages and game over), the HTTP client against `MockRestServiceServer`, and the controller with `MockMvc` (status codes, problem documents, validation).

### Results

Measured against the live API with the CLI autoplayer. Later rows are later versions of the strategy; the 28 and 30 Oct runs are 10 games each, capped at 500 actions per game.

| Strategy version | Games | Min | Avg | Max | Reached 1000+ |
| --- | --- | --- | --- | --- | --- |
| 27 Oct: potion at ≤3 lives, 100 gold reserve, first affordable upgrade rebought | 5 | 3348 | 4216 | 5259 | 5/5 |
| 28 Oct: upgrade ladder, penalty fix | 10 | 492 | 6162 | 11951 | 8/10 |
| 28 Oct: + life valued at 100 gold, no *Gamble* at full health | 10 | 954 | 8680 | 27864 | 9/10 |
| 28 Oct: + trap phrases and per-game distrust | 10 | 3917 | 9387 | 20293 | 10/10 |
| **30 Oct: + ad families keyed by task and label (current default)** | 10 | 4581 | 14797 | 43739 | **10/10** |

What the rows say: climbing the upgrade ladder roughly doubled the average and the ceiling (a dragon at level 100+ is handed easy ads worth hundreds of gold), but it exposed a failure mode the first version had merely been lucky about. Every lost game, in every version, died between turns 29 and 48 at level 2 to 9, and the history dumps all showed the same "Sure thing" trap ads failing in a row. Recognising the trap phrase and distrusting any ad family that fails twice removed those deaths; the more cautious life valuation is kept because it costs nothing late in the game, when rewards dwarf the life cost.

The 30 Oct run also showed two things worth knowing: a level‑298 dragon did die at turn 321, so "never dies" is too strong, and the best game (level 326, 43739 points) was cut short at turn 297 by the API's rate limit, which the 10/20/30‑second waits of that version could not outlast; the longer, non‑counting waits described above are the result.

The samples are small and scores vary a lot from run to run (the same strategy produced 4581 and 43739 in one batch), so treat the averages as indicative. The floor is the number that matters for the task, and every knob in the table is a `mugloar.strategy.*` property, so each row can be reproduced.

## Frontend

A Vue 3 single‑page app in [frontend/](frontend/) that covers the "Frontend adventure" and doubles as a cockpit for the backend's strategy.

```
src/api/         Typed fetch client (gameApi) and the API's TypeScript shapes; ids are validated before they reach a URL
src/stores/      Pinia store `game`: the single source of truth for the game on screen and all actions
src/views/       HomeView (start / resume) and PlayView (the game screen), wired through Vue Router
src/components/  StatusBar, AdBoard + AdCard, ShopPanel, AutoplayControls, ReputationPanel, HistoryLog, banners/toast
src/utils/       Formatting helpers
```

### What the player gets

- **Status bar** with lives (hearts turn amber/red as they run out), gold, score, level and turn, sticky at the top on every screen size.
- **Message board** with every ad decoded and assessed by the backend: success chance meter, expected value, advisor score, risk badge, expiry, and notes such as "May hurt reputation" or "Decoded from base64". The best eligible ad is marked **Recommended**; ads outside the current risk budget are dimmed. Sort by advisor score, reward, chance or urgency, or hide risky ads entirely.
- **Shop** showing what is affordable, how many times each item was bought, and the item the strategy would buy now. Unaffordable items are disabled, so no turn is wasted on a failed purchase.
- **Advisor panel**: play one turn, run autoplay turn by turn with an adjustable pause (the board refreshes after every step, so you can watch the reasoning), stop at any time, or hand the rest of the game to the server. While the server plays, a panel takes the board's place: it explains what the server is doing turn after turn, counts the time, and rotates ten lines of dragon lore, since a good run can take minutes. A server run that stops before the game is over says why in an info banner.
- **History** of every action with the outcome, the API's message and, for advisor moves, the reason it was chosen.
- **Reputation** on demand, a game‑over banner that says whether the 1000‑point target was met, and a toast after each action.
- The last game id is remembered in `localStorage`, so a refresh or the "Resume last game" button brings you back.

### State management and error handling

All server interaction goes through the Pinia store. Components receive props and emit events only, which keeps them trivially testable. Every action clears the previous error, records the returned turn, refreshes the board, and on failure shows the backend's problem detail in one banner. A rejected action (expired ad, game over) resyncs the game state from the server; an action whose outcome is unknown (dropped connection, gateway error) reloads state and history, since the backend may have taken the turn anyway. Every switch of game bumps a generation counter and late answers about a game no longer on screen are dropped, and the history is merged by turn number, so a reload that already listed a turn the server was still reporting cannot double it. Autoplay is a cancellable loop in the store, and concurrent actions are ignored while one is in flight. A game that cannot be loaded says whether it is unknown to the server or the server could not be reached, with a retry; a game the backend has forgotten since (it keeps games in memory, so a restart does that) is taken off the screen the moment any request says so.

### Responsiveness and compatibility

Mobile‑first CSS with a single breakpoint at 900px (board + sidebar) and 1100px (two‑column board). Only widely supported CSS is used (grid, flexbox, custom properties, `prefers-color-scheme` for a dark theme), no container queries or `:has()`. Vite targets modern evergreen browsers; the build is tested in Chromium and the layout at 375px, 768px and desktop widths.

### Tests

`pnpm test:unit` runs 70 Vitest tests: the API client against a mocked `fetch` (URL building, id validation, problem documents, network failures), the store with a mocked API (start/load/solve/buy, error resync, the autoplay loop with fake timers, server‑side finish), and the components with Vue Test Utils (rendering, sorting/filtering, emitted events, disabled states).

## Known limitations and possible next steps

- Game sessions live in the backend's memory. They are lost on restart and never evicted; a real deployment would put them behind the same `GameSessionRegistry` interface in Redis or a database and expire finished games. The game server itself forgets a game after a while of inactivity, so an old game reopened later ends with "game over" at its last known score.
- There is no authentication: anyone who can reach the API can drive any game it knows. Fine for a test task, not for the internet.
- The success rates behind the probability labels are assumed, not measured: the game never exposes numbers, and this project has not run enough games to calibrate them. They are one table in `Probability`, and every other strategy knob is a property.
- The autoplayer runs a game on one request thread; a long "finish game" call can take a minute or two, and several minutes when the game API rate-limits the run and the player waits it out. Streaming turns over SSE would make that nicer for the UI; the turn-by-turn autoplay shows every move as it happens.
- Frontend tests cover the client, the store and the components; the two views are exercised manually rather than with an end‑to‑end test.

## Use of AI tools

Claude Code was used as a pair‑programming assistant throughout: scaffolding, drafting code and tests, probing the game API, and documentation. All design decisions, the game strategy and the final code were reviewed and are owned by the author.
