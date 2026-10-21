# Mystical Manor — Docker Compose REST API

A small browser-based adaptation of my original Kotlin console adventure game. Players can create an Artist, Explorer or Mage, explore the manor, discover paintings, collect items and keep progress in PostgreSQL.

**Stack:** Kotlin 2.0.21, Ktor 3.0.3, PostgreSQL 17, JDBC, HTML/CSS/JavaScript, Docker Compose, Gradle, Postman.

## Architecture

Browser / Postman → HTTP `localhost:8081` → **backend** (Kotlin/Ktor, internal port 8080) → **db** (PostgreSQL, internal port 5432) → **manor-data** named volume.

The Compose network provides DNS resolution for the hostname `db`. The database is not published to the host. The backend waits for the database healthcheck.

## Quick start

1. Install Docker Desktop, enable Linux containers and start Docker Engine.
2. In the project root, create a local environment file: `copy .env.example .env` (Windows CMD) or `cp .env.example .env` (macOS/Linux). Change `POSTGRES_PASSWORD` to a unique password. **Never commit `.env`.**
3. Validate: `docker compose config -q`.
4. Start: `docker compose up --build -d` (or `docker compose up` in foreground).
5. Check: `docker compose ps` and `curl http://localhost:8081/api/health`.
6. Play: http://localhost:8081.

If port 8081 is busy, change `APP_PORT` in `.env` and update the Postman `baseUrl` collection variable.

**Persistence demonstration:** create a player, perform actions, run `docker compose down`, then `docker compose up -d`, and reload the same player ID. `docker compose down -v` **deletes** database data and must not be used for persistence testing.

**Existing database/password:** PostgreSQL initializes the user's password only on a fresh data directory. If credentials are changed after volume initialization, update the database user's password; deleting the volume is only appropriate when existing saves can be discarded.

## REST API reference

Base URL: `http://localhost:8081`. All player endpoints use JSON except DELETE (204, empty response). Error responses generally contain `{"error":"..."}`.

| Method | Endpoint | Purpose | Expected success |
|---|---|---|---|
| GET | `/api/health` | Backend health | 200 |
| GET | `/api/players` | List all saved players | 200 |
| POST | `/api/players` | Create player | 201 |
| GET | `/api/players/{id}` | Get player by ID | 200 |
| PUT | `/api/players/{id}` | Update name and class | 200 |
| DELETE | `/api/players/{id}` | Delete player and associated items/paintings | 204 |
| POST | `/api/players/{id}/actions` | Perform a game action | 200 |
| GET | `/api/players/{id}/inventory` | List inventory | 200 |
| GET | `/api/players/{id}/paintings` | List discovered paintings | 200 |

### JSON examples

Create player (`POST /api/players`):

```json
{"name":"Anastasia","characterClass":"Artist"}
```

Update player (`PUT /api/players/1`):

```json
{"name":"Updated Hero","characterClass":"Explorer"}
```

Supported character classes: `Artist`, `Explorer`, `Mage`. Updating a class changes maximum health and its starting item, while retaining game progress. Player names must contain 1–60 characters.

Perform action (`POST /api/players/1/actions`):

```json
{"action":"gallery"}
```

Supported actions: `hall`, `gallery`, `library`, `explore`, `ability`. The game state, inventory and paintings are stored in PostgreSQL. `GET /api/players/{id}` returns a JSON object containing `id`, `name`, `characterClass`, `health`, `maxHealth`, `gold`, `experience`, `room`, `inventory`, `paintings`, and `message`.

Errors: `400` for invalid IDs or invalid player/action values, `404` for missing players. `DELETE` returns `204` without JSON when successful. Request bodies must be valid JSON.

## Postman collection

Import `postman/MysticalManor.postman_collection.json` using **Postman → Import → File**. No account is required for the desktop lightweight/local workflow where available; if Postman prompts for sign-in, follow its on-screen account instructions. The collection contains all nine endpoints and 11 sequential test requests, including CRUD and a 404 check after deletion.

1. Start the containers.
2. Open the collection → **Variables**; set `baseUrl` to `http://localhost:8081` (or your configured port).
3. Send **02 Create player**; its Tests script saves the returned ID as `playerId`.
4. Send 03–11 in order, or run the whole collection with **Run**. **10 Delete player** intentionally deletes the Postman test character.
5. Verify the green test results and screenshot the collection run summary.

## Database demonstration

```bash
docker compose exec db psql -U manor_user -d mystical_manor
```

```sql
SELECT * FROM players;
SELECT * FROM inventory;
SELECT * FROM paintings;
SELECT p.id, p.name, pt.title FROM players p LEFT JOIN paintings pt ON pt.player_id=p.id ORDER BY p.id;
```

`players` is the parent table; `inventory` and `paintings` reference it with `ON DELETE CASCADE`. Deleting a player deletes related rows. If you customize database username or name in `.env`, change the `psql` command accordingly.

