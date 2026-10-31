# Test Task: Dragons of Mugloar

## Overview

**Objective:** Solve an adventure on <https://dragonsofmugloar.com/> that demonstrates your back‑end and front‑end skills.

### Requirements for Fullstack engineer

- Create a web app based on the provided instructions.
- Ensure responsiveness and cross‑browser compatibility.
- Implement state management.
- Develop a program that reaches 1000+ points in the game.
- Ensure proper error handling and input validation.
- Write unit tests to verify the functionality of your code.

### Submission Guidelines

- **Deadline:** submit the completed task by the end of 01.11.2026.
- **Format:** upload the code to a public repository on GitHub or GitLab and share the link.
- **Documentation:** include a README with instructions on how to run the project and any other relevant information.

### Evaluation Criteria

- Code quality and organization
- Adherence to the requirements
- Creativity and problem‑solving approach
- Use of best practices and design patterns
- Documentation and clarity

### Getting Started

Visit the test task page <https://dragonsofmugloar.com/> for detailed instructions and resources.

### Next Steps

Upon receiving the submission, the team reviews the work and contacts the candidate about the next steps. Feedback is expected within one week after the submission deadline.

Be prepared to explain the implementation approach and decision‑making process, including if, when, and how AI tools were used during the task, and to make live code changes and discuss the solution during follow‑up discussions.

## Game brief (from the site)

### Backend adventure

Create a program (using the API documented at <https://dragonsofmugloar.com/doc/>) which:

1. Starts a new game
2. Fetches a list of ads
3. Picks the best ads to solve and solves them
4. Optionally buys items to improve its chances
5. Repeats from step 2 until lives run out

The solution is acceptable when the application is reliably able to achieve a score of at least 1000 points.

### Frontend adventure

Create a web app (using the same API) which:

1. Allows the user to start a game
2. Fetches and displays the list of ads
3. Allows the user to pick which ones to solve
4. Allows the user to purchase items from the shop
5. Displays the player's score, gold and lives

The solution is acceptable when it is possible to play the game and gain at least 1000 points. Extra credit is given for creative tools helping the player make decisions on ads.

## API reference (summary)

Base URL: `https://dragonsofmugloar.com/api/v2`

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/game/start` | Start a new game → `gameId, lives, gold, level, score, highScore, turn` |
| `POST` | `/{gameId}/investigate/reputation` | Reputation → `people, state, underworld` |
| `GET` | `/{gameId}/messages` | List ads → `adId, message, reward, expiresIn, encrypted, probability` |
| `POST` | `/{gameId}/solve/{adId}` | Attempt an ad → `success, lives, gold, score, highScore, turn, message` |
| `GET` | `/{gameId}/shop` | List shop items → `id, name, cost` |
| `POST` | `/{gameId}/shop/buy/{itemId}` | Buy an item → `shoppingSuccess, gold, lives, level, turn` |

Notes observed while probing the API:

- The game starts with 3 lives, 0 gold, level 0.
- Each ad carries a textual `probability` (e.g. "Piece of cake", "Quite likely", …) and an `encrypted` flag (`null`, `1` or `2`).
- Buying an item consumes a turn even when the purchase fails.
- An unknown, expired or already solved `adId` yields HTTP 400; an unknown `gameId` yields HTTP 404.

### Further API findings (probed 31 Oct 2026)

**Turns and board**

- Costs a turn: solve, any buy attempt (failed included), `investigate/reputation`. Free: `GET messages`, `GET shop`.
- Board: 10 ads. `expiresIn` starts at 7 (plain) or 2–3 (encrypted), −1 per turn, solvable at 1, removed the next turn. Solved and expired ads are replaced immediately; the opening board expires as a whole at turn 7.
- Solving an already solved, expired or foreign ad: 400.

**Encryption**

- `encrypted: 1` = Base64, `2` = ROT13; applied to `adId`, `message`, `probability`. `reward` and `expiresIn` are plain.
- Solve requires the decoded `adId` (encoded → 400).
- Encrypted ads appear from turn ~16–23, reward 130–200, mostly *Risky* or worse.

**Shop, lives, level, score**

- Items, fixed across games: `hpot` 50; `cs`, `gas`, `wax`, `tricks`, `wingpot` 100; `ch`, `rf`, `iron`, `mtrix`, `wingpotmax` 300.
- `hpot`: +1 life, no cap (9 observed), allowed at full lives.
- 100-gold items: +1 level. 300-gold items: +2 levels. Rebuys stack (68/68 purchases).
- `score` = sum of rewards; purchases do not reduce it.
- `highScore` is always 0.

**Labels** (418 solves, diamond traps excluded)

| Label | Success | Rate |
| --- | --- | --- |
| Sure thing | 60/60 | 100% |
| Piece of cake | 125/135 | 93% |
| Walk in the park | 45/55 | 82% |
| Quite likely | 17/27 | 63% |
| Hmmm.... | 8/23 | 35% |
| Gamble | 13/20 | 65% |
| Risky | 9/18 | 50% |
| Rather detrimental | 4/19 | 21% |
| Playing with fire | 5/20 | 25% |
| Suicide mission | 3/35 | 9% |
| Impossible | 0/6 | 0% |

- "Steal super awesome diamond … from …": 0/32, labelled *Sure thing* or *Piece of cake*.
- Diamond ads appear after the 5th successful "Steal … and share some of the profits with the people" ad (3/3 games; none in 5 games with ≤4). Each such steal: people ≈ +1, state ≈ −2.
- Solve messages: "You successfully solved the mission!", "You failed on the mission!", "You were defeated on your last mission!" (last life), "You fell into a trap set up, by people who did not appreciate your dealings." (diamond ads).
- Difficulty rises with the turn: from turn ~60, <5% of ads are *Walk in the park* or better at levels ≤26. Rewards rise too (median "Help defending" 142 at turns 20–39, 202 at 80+).
- Reputation: decimals; `underworld` always 0; no changes after turn ~30.

**Errors and transport**

| Case | Status | Body |
| --- | --- | --- |
| Game over (every endpoint) | 410 | `{"status":"Game Over"}` |
| Unknown or expired game | 404 | HTML "Not Found" |
| Unknown route or wrong method | 404 | HTML "Cannot GET /api/v2/…" |
| Bad ad id | 400 | HTML "Bad Request" |
| Unknown item id | 200 | `shoppingSuccess: false`, turn consumed |
| User-Agent `Python-urllib/*`, `libwww-perl/*` | 403 | Cloudflare "error code: 1010" |

- Idle timeout ~10 min (alive at 9 min, 404 at 10.6 min); any request, reads included, resets it (a game polled with `GET messages` every minute survived 16 min).
- Routes are case-insensitive and accept a trailing slash; ids are case-sensitive.
- CORS: `Access-Control-Allow-Origin: *`.
- Latency: solve 0.6–1.3 s, start ~1 s, everything else ~50 ms.
- No 429 at ~1 req/s (1,332 requests in 25 min).
- No per-game serialisation: concurrent actions can share a turn number and lose updates (4 parallel `hpot` buys with gold for 1: two `shoppingSuccess: true`, one potion applied, one turn lost).
- /doc omits `encrypted` and `probability`, and types `reward` and `shoppingSuccess` as String (actual: number, boolean).
