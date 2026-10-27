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
- An unknown `adId`/`gameId` yields HTTP 400.
