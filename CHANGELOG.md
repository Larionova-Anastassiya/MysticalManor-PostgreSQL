# Changes from MysticalManor_Part1_Complete

**Unchanged:** original console game classes, Gradle versions, Dockerfile, Compose services, existing player CRUD and gameplay routes.

**Changed:** `WebMain.kt` (initialization + route registration), `index.html`, `style.css`, `script.js` (add mini-game).

**New:** `CursedPainting.kt` (SQL models, puzzle mechanics, routes), `postman/MysticalManor_CursedPainting.postman_collection.json`, `API.md`, this changelog.

The original `players`, `inventory`, and `paintings` tables remain unchanged. New tables are created with `CREATE TABLE IF NOT EXISTS`.
