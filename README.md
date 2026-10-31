# Dragons of Mugloar

A full-stack solution to the [Dragons of Mugloar](https://dragonsofmugloar.com/) test task: a Spring Boot backend that plays the game on its own and reaches the 1000-point target, and a Vue 3 frontend where a human plays the same game with the backend's advice. The assignment is kept verbatim in [TASK.md](TASK.md).

| Layer | Technology |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1 (Web MVC, RestClient, Bean Validation, Actuator), Maven wrapper, JUnit 5, AssertJ, Mockito |
| Frontend | Vue 3.5, TypeScript, Vite 8, Pinia, Vue Router, Vitest, oxlint + ESLint, Prettier |

```
.
├── backend/    Spring Boot application: game API client, strategy, REST API, CLI autoplayer
├── frontend/   Vue 3 single-page application
└── TASK.md     The assignment text and API summary
```

## Quick start

You need JDK 25 with `JAVA_HOME` pointing to it, Node.js 22.18+ or 24+ (tested on 24 and 26) and [pnpm](https://pnpm.io/) 10+. Maven ships as the wrapper. An older LTS from 17 up works with `java.version` lowered in `backend/pom.xml`; the code uses nothing beyond Java 21.

Two terminals:

```bash
cd backend && ./mvnw spring-boot:run          # API on http://localhost:8080 (Windows: mvnw.cmd)
```

```bash
cd frontend && pnpm install && pnpm dev       # UI on http://localhost:5173, proxies /api to 8080
```

Open <http://localhost:5173>, press **Start a new game**, and pick ads yourself or press **Autoplay**. Health check: <http://localhost:8080/actuator/health>.

## Backend

```
client/    MugloarApiClient (interface) + MugloarRestClient (Spring RestClient) + wire DTOs
domain/    Ad, GameState, ShopItem, Probability, AdDecoder: the game model, free of Spring and HTTP
strategy/  AdEvaluator, ShoppingPolicy, GameStrategy: how to play; pure functions over the domain
service/   GameSession, GameEngine, AutoPlayer, GameService: stateful orchestration per game
web/       GameController, GlobalExceptionHandler, view DTOs: the REST API for the frontend
cli/       AutoplayRunner: the "program that reaches 1000+ points" as a command-line run
config/    Typed, validated @ConfigurationProperties for the API connection and the strategy knobs
```

Each layer depends only on the ones below it. The strategy is plain code with no I/O, so it is unit-tested directly and the REST API reuses it for recommendations.

### How it plays

Every turn the strategy makes one decision, in this order:

1. **Survive.** Lives at or below 3 and a healing potion affordable: buy one.
2. **Invest.** Climb the upgrade ladder with the gold above a 100-gold reserve. Rungs are the configured upgrade order and the next rung is the one bought the fewest times, so every upgrade is bought once before any is bought twice; an unaffordable rung is skipped rather than saved for. Upgrades raise the level, and the server rewards a high level with easier ads.
3. **Earn.** Rank the ads and solve the best eligible one.

- **Score** is `successRate × reward − (1 − successRate) × lifeCost`. The success rate is an assumed mapping of the game's labels ("Sure thing" = 1.0 … "Impossible" = 0; the game never exposes numbers), a life is valued at 100 gold and more as lives run out, and ads that suggest a reputation hit (steal, kidnap, …) have their expected gold discounted first, so the penalty can only lower a score.
- **Eligible** means the success rate clears a threshold that tightens with fewer lives: 60% at three or more lives (so a *Gamble* is never taken at full health), 80% at one. When a potion is unaffordable, ads are judged as if one life fewer remained. With nothing eligible the strategy prefers a potion, which rolls the board over, and only then the least dangerous ad.
- **Labels can lie.** From about turn 25 the board offers "Steal super awesome diamond …" ads labelled *Sure thing* with rewards of 300+ gold, and they fail every time. Ads matching a configured trap phrase are rated 0% whatever the label says. Ads are also grouped into families (the first words of the text with names and places skipped, plus the label), and a family that fails twice is distrusted for the rest of that game, so an unknown trap costs at most two lives. Both show up as notes on the ad in the UI.
- **Encoded ads** (`encrypted = 1` is Base64, `2` is ROT13) are decoded with the hinted scheme and verified by checking that the decoded label is a known one, falling back to the other schemes, so a renumbered scheme cannot silently break the player.

Everything above is tunable through `mugloar.strategy.*` properties (see `StrategyProperties`); the defaults are what the results below were measured with.

<details>
<summary>Why the trap rules exist: the history of a game that died</summary>

Every early death looked like this. The reward discount for "steal" was not enough, because 60% of 322 still beat every honest ad.

```text
 25 XX  L3 g 192 lv  6 sc  892 | Steal super awesome diamond pan from Kassiopeia Clemens | Best eligible ad: Sure thing, reward 228
 26 buy L4 g 142 lv  6 sc  892 | Healing potion                                           | Lives are low (3), buying a healing potion
 30 XX  L3 g 164 lv  8 sc 1114 | Steal super awesome diamond turnips from Cosma Isaacson  | Best eligible ad: Sure thing, reward 280
 34 XX  L3 g  64 lv  8 sc 1114 | Steal super awesome diamond horse from Sonja Courtier    | Best eligible ad: Sure thing, reward 322
 ...
 48 XX  L0 g  17 lv  8 sc 1317 | Steal super awesome diamond horse from Yahui Rowbottom   | Best eligible ad: Sure thing, reward 329
```

The label is part of the family key so that two failed *Hmmm* ads never discredit the *Piece of cake* ads of the same kind.

</details>

### Robustness

- **Validation.** Ids are checked against a strict pattern before anything is forwarded upstream and request bodies are bean-validated. Every failure is an RFC 9457 problem document: `400` invalid input, `404` unknown game, `409` game over, `422` action not possible (expired ad, unaffordable item, empty board), `502`/`503` upstream failure.
- **No wasted turns.** Unaffordable or unknown purchases are refused locally, because the game charges a turn even for a failed purchase.
- **Unanswered actions.** An action the API never answered may still have been carried out, and a purchase is not safe to repeat. The session is marked uncertain and every purchase, manual or automated, is refused until the next answer has refreshed gold and lives. An unanswered purchase is settled by that answer's gold: either the one action explains it, so the purchase never happened, or it does not, so it did and is counted. A lost solve answer is harmless, since the API rejects the same ad a second time.
- **Rate limits.** A strong dragon makes about ten requests a second and the API answers 429 after roughly six hundred of them. The player waits 15, 30, 45 and then 60 seconds, up to six times in a row, instead of counting it as a failure. Other transient errors and expired ads refresh the board and retry; three failures in a row end the run.
- **Game over upstream.** HTTP 410 closes the session whatever local bookkeeping says, and so does a 404, since the game server forgets an idle game. A run that hits either ends cleanly with that reason and the UI shows the final state.
- **Concurrency.** Each action runs under its game's lock, so a click and an autoplay request cannot interleave. Reads never wait: state, purchases and distrusted families are one immutable snapshot swapped atomically, so a response never mixes two turns and the UI can follow a game the server is playing.

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
| `GET` | `/api/games/{id}/history` | Every action taken, with the strategy's reasoning and the state after it |

### Command line

The `autoplay` profile starts no web server. It plays `mugloar.autoplay.games` games with the strategy, prints a summary and exits non-zero if any game ended below 1000 points or could not be started, so it works as a regression check in a shell or CI. Each game stops after `mugloar.strategy.max-turns` actions (default 1000, the cap the UI's *Finish game* uses too); the server keeps the score either way.

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=autoplay -Dspring-boot.run.arguments=--mugloar.autoplay.games=3
```

### Results

Measured against the live API with the CLI. Later rows are later versions of the strategy; the 28 and 30 Oct runs are 10 games each, capped at 500 actions per game.

| Strategy version | Games | Min | Avg | Max | Reached 1000+ |
| --- | --- | --- | --- | --- | --- |
| 27 Oct: potion at ≤3 lives, 100 gold reserve, first affordable upgrade rebought | 5 | 3348 | 4216 | 5259 | 5/5 |
| 28 Oct: upgrade ladder, penalty fix | 10 | 492 | 6162 | 11951 | 8/10 |
| 28 Oct: + life valued at 100 gold, no *Gamble* at full health | 10 | 954 | 8680 | 27864 | 9/10 |
| 28 Oct: + trap phrases and per-game distrust | 10 | 3917 | 9387 | 20293 | 10/10 |
| **30 Oct: + ad families keyed by task and label (current default)** | 10 | 4581 | 14797 | 43739 | **10/10** |

Climbing the upgrade ladder roughly doubled the average and the ceiling, but every lost game, in every version, died between turns 29 and 48 to the "Sure thing" trap ads; recognising the trap phrase and distrusting a family that fails twice removed those deaths. The samples are small and scores vary a lot from run to run (4581 and 43739 came from one batch), so treat the averages as indicative. The floor is the number that matters for the task.

<details>
<summary>More on the runs</summary>

The more cautious life valuation is kept because it costs nothing late in the game, when rewards dwarf the life cost. The 30 Oct run also showed two things worth knowing: a level-298 dragon did die at turn 321, so "never dies" is too strong, and the best game (level 326, 43739 points) was cut short at turn 297 by the API's rate limit, which the 10/20/30-second waits of that version could not outlast; the longer, non-counting waits above are the result. Every knob in the table is a `mugloar.strategy.*` property, so each row can be reproduced.

</details>

## Frontend

```
src/api/         Typed fetch client (gameApi) and the API's TypeScript shapes; ids are validated before they reach a URL
src/stores/      Pinia store `game`: the single source of truth for the game on screen and all actions
src/views/       HomeView (start / resume) and PlayView (the game screen), wired through Vue Router
src/components/  StatusBar, AdBoard + AdCard, ShopPanel, AutoplayControls, FinishingPanel, ReputationPanel, HistoryLog, banners/toast, ViewTabs + SwipeTrack (phone navigation)
src/composables/ useMediaQuery
src/utils/       Formatting helpers and the dragon lore shown while the server plays
```

### What the player gets

- **Status bar** with lives (hearts turn amber, then red), gold, score, level and turn, sticky on every screen size.
- **Message board** with every ad decoded and assessed by the backend: success chance meter, expected value, advisor score, risk badge, expiry, and notes such as "May hurt reputation" or "Decoded from base64". The best eligible ad is marked **Recommended**; ads outside the risk budget are dimmed. Sort by score, reward, chance or urgency, or hide risky ads.
- **Shop** showing what is affordable, how often each item was bought, and the item the strategy would buy now. Unaffordable items are disabled, so no turn is wasted on a failed purchase.
- **Advisor**: play one turn, run autoplay turn by turn with an adjustable pause (the board refreshes after every step, so the reasoning can be watched), stop at any time, or hand the rest of the game to the server. While the server plays, a panel takes the controls' place: it explains what is happening, counts the time and rotates ten lines of dragon lore, since a good run takes minutes. A run that stops early says why in an info banner.
- **History** of every action with the outcome, the API's message and, for advisor moves, the reason it was chosen; **reputation** on demand; a game-over banner that says whether the target was met; a toast after each action.
- **Resume**: the last game id is kept in `localStorage`, so a refresh or the "Resume last game" button brings you back.
- **On a phone** the screen becomes three views, **Board**, **Shop** and **Advisor** (with reputation and the history), with a tab bar under the status bar: swipe, or tap a tab. The tabs carry badges: how many ads fit the risk budget, a dot when the strategy would buy something now, and a pulse while autoplay runs.

### State and errors

All server interaction goes through the Pinia store; components take props and emit events, which keeps them simple to test. Every action clears the previous error, records the returned turn and refreshes the board; a failure shows the backend's problem detail in one banner. A rejected action (expired ad, game over) resyncs the state from the server, and an action whose outcome is unknown (dropped connection, gateway error) reloads state and history, since the backend may have taken the turn anyway. A generation counter drops late answers about a game no longer on screen, the history is merged by turn number so a reload cannot double a turn, and a game the backend has forgotten (it keeps games in memory, so a restart does that) is taken off the screen the moment any request says so. Autoplay is a cancellable loop in the store, and a second action is ignored while one is in flight.

### Responsiveness

Below 900px the game screen is the three swipeable views; from 900px the board sits next to a sidebar, and from 1100px the board goes two-column. The swiping is CSS scroll snapping, so the browser provides the gesture, the momentum and the snap and tells a vertical scroll from a horizontal swipe on its own; there is no gesture library. A tab tap scrolls the track, a settled swipe updates the tab, off-screen views are `inert` so focus and screen readers stay on the visible one, and the tab bar is a `tablist` with arrow-key navigation. The two layouts are picked by a reactive `matchMedia` query and share the store. Only widely supported CSS is used (grid, flexbox, custom properties, scroll snapping, `prefers-color-scheme` for a dark theme); the layout is tested in Chromium at 375px, 768px and desktop widths.

## Tests

| Suite | Command | What it covers |
| --- | --- | --- |
| Backend, 142 tests | `cd backend && ./mvnw test` | Label parsing and decoding, scoring and eligibility, shopping, the strategy defaults as bound by Spring Boot, the CLI exit code, the turn loop against an in-memory fake of the game API (expired ads, outages, lost answers, rate limits, game over), the HTTP client with `MockRestServiceServer`, the controller with `MockMvc` |
| Frontend, 82 tests | `cd frontend && pnpm test:unit` | The API client against a mocked `fetch`, the store with a mocked API (actions, error resync, the autoplay loop with fake timers, server-side finish, forgotten games), the components with Vue Test Utils (rendering, sorting and filtering, events, the tab bar and the swipe track) |

The frontend also runs `pnpm lint` (oxlint + ESLint), `pnpm type-check` (vue-tsc) and `pnpm build`.

## Known limitations

- **Sessions live in the backend's memory.** They are lost on a restart and never evicted, and the game server forgets an idle game too. Fix: a scheduled sweep of finished and idle games, or Redis behind the existing `GameSessionRegistry` interface with a TTL.
- **No authentication or ownership.** Anyone who reaches the API can drive any game; two players on one game interleave safely, thanks to the lock, but silently. Fix: an owner token issued at game start and checked on every action, Spring Security in front, and a rate limit on game creation.
- **One player's run blocks the others.** A "finish game" call holds its request thread and the game's lock for minutes, longer when a rate limit is waited out, so every other request on that game waits. Fix: finish in chunks of `maxTurns` from the UI first, then an asynchronous, cancellable run with progress pushed over SSE.
- **No push updates.** A second browser on the same game sees a change only when it asks. Fix: an SSE stream of turns, merged by the store's existing turn merge.
- **The rate limit caps a game** around turn 300 in practice; the waits keep it alive but slow. Fix: measure the limit and pace requests below it.
- **Success rates are assumed**, not measured; they are one table in `Probability`. Fix: log label and outcome per ad over fifty CLI games and replace the table with the measured rates.
- **No end-to-end tests.** The two views are checked by hand. Fix: Playwright against a backend profile backed by the fake game API from the tests.
- **Requests are never cancelled.** Late answers are dropped by the generation counter rather than aborted. Fix: an `AbortController` per generation.

## Use of AI tools

Claude Code was used as a pair-programming assistant throughout: scaffolding, drafting code and tests, probing the game API, and documentation. All design decisions, the game strategy and the final code were reviewed and are owned by the author.
