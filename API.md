# REST API Reference — Mystical Manor: The Cursed Painting

Base URL: `http://localhost:8081`. JSON requests use `Content-Type: application/json`.

| Method | URL | Body / parameters | Success | Errors |
|---|---|---|---|---|
| GET | `/api/health` | None | 200 `{ "status": "ok" }` | — |
| GET | `/api/players` | None | 200 array of PlayerView | — |
| POST | `/api/players` | `{"name":"Ada","characterClass":"Artist"}` | 201 PlayerView | 400 invalid name/class |
| GET | `/api/players/{id}` | positive integer ID | 200 PlayerView | 404 missing |
| PUT | `/api/players/{id}` | `{"name":"Ada","characterClass":"Mage"}` | 200 PlayerView | 400 invalid, 404 missing |
| DELETE | `/api/players/{id}` | positive ID | 204 no body | 400 invalid, 404 missing |
| POST | `/api/players/{id}/actions` | `{"action":"gallery"}`; allowed: hall, gallery, library, explore, ability | 200 PlayerView | 400 invalid, 404 missing |
| GET | `/api/players/{id}/inventory` | positive ID | 200 array of strings | 404 missing |
| GET | `/api/players/{id}/paintings` | positive ID | 200 array of strings | 404 missing |
| GET | `/api/paintings` | None | 200 array of PaintingInfo | — |
| GET | `/api/paintings/{id}` | positive painting ID | 200 PaintingInfo | 400 invalid, 404 missing |
| GET | `/api/paintings/{id}/attempts` | positive painting ID | 200 array of PuzzleView | 400 invalid, 404 missing |
| GET | `/api/players/{id}/attempts` | positive player ID | 200 array of PuzzleView | 400 invalid, 404 missing |
| GET | `/api/attempts` | None | 200 array of PuzzleView (up to 100 latest) | — |
| GET | `/api/attempts/{id}` | positive attempt ID | 200 PuzzleView | 400 invalid, 404 missing |
| POST | `/api/attempts` | `{"playerId":1,"paintingId":1}` | 201 PuzzleView | 400 not in Gallery / undiscovered, 404 missing player/painting |
| PATCH | `/api/attempts/{id}` | `{"runes":["SUN","MOON","STAR"]}` | 200 PuzzleView | 400 invalid/finished, 404 missing |
| DELETE | `/api/attempts/{id}` | positive attempt ID | 204 no body | 400 invalid, 404 missing |

## Response shapes

`PlayerView`: `id`, `name`, `characterClass`, `health`, `maxHealth`, `gold`, `experience`, `room`, `inventory` (array), `paintings` (array), `message`.

`PaintingInfo`: `id`, `title`, `description`, `difficulty`, `rewardGold`, `rewardXp`.

`PuzzleView`: `id`, `playerId`, `paintingId`, `paintingTitle`, `status` (`ACTIVE`, `WON`, `LOST`), `moves`, `maxMoves`, `lastExact`, `lastPresent`, `guesses` (array), `createdAt`. **The secret sequence is never returned.**

Errors use `{ "error": "message" }`. Malformed JSON may be rejected by Ktor with a 4xx response; application-level validation is handled for the documented fields.

## Gameplay sequence

1. Create a player.
2. `POST /api/players/{id}/actions` with `{"action":"gallery"}`.
3. Explore (`{"action":"explore"}`) to discover *The Starry Night*.
4. `GET /api/paintings` to find its ID.
5. `POST /api/attempts` with player and painting IDs.
6. Submit three runes per `PATCH` (up to 6 turns). `lastExact` counts exact positions; `lastPresent` counts matching symbols in different positions, without double-counting duplicates.
7. On win, the backend updates gold/XP and inserts `Enchanted Rune` into the existing inventory table, in the same database transaction.

The puzzle secret stays in PostgreSQL. Refreshing the page does not alter stored player progression; the currently open puzzle can be retrieved through `/api/players/{id}/attempts` or `/api/attempts/{id}`.

## Data relationships

- `players` → `inventory`: one-to-many, existing table.
- `players` → `paintings`: one-to-many, existing table.
- `players` → `puzzle_attempts`: one-to-many, foreign key and `ON DELETE CASCADE`.
- `painting_catalog` → `puzzle_attempts`: one-to-many, foreign key and `ON DELETE RESTRICT`.

SQL uses JDBC `PreparedStatement`, transactions, and `SELECT ... FOR UPDATE` when submitting guesses. A repeated guess after completion is rejected, preventing duplicate rewards.

## Notes

The mini-game uses a 3-rune code selected from four symbols. This is a small educational Mastermind-style puzzle, not a cryptographic secret. The original player-ID API does not have authentication; it is intended for local coursework, not a public production deployment.
