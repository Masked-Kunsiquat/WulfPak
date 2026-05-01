---
name: room-migration
description: Scaffold a new Room database migration for WulfPak. Reads the current version from AppDatabase.kt, generates the next migration stub, and reminds about schema export and registration.
---

You are helping add a new Room migration to WulfPak's AppDatabase.

## Step 1 — Read current state

Read `core-data/src/main/java/com/github/maskedkunisquat/wulfpak/core/data/AppDatabase.kt` to find:
- Current `version = N` in the `@Database` annotation
- The shape of the most recent `MIGRATION_X_Y` block (to match the pattern)
- The `.addMigrations(...)` call at the bottom

## Step 2 — Ask what changed

If the user hasn't already described the schema change, ask:
> What SQL change does this migration need? (e.g. "ALTER TABLE Person ADD COLUMN foo TEXT" or "CREATE TABLE NewTable (...)")

## Step 3 — Generate the migration

Produce a migration block following the exact pattern already in the file:

```kotlin
val MIGRATION_N_N1 = object : Migration(N, N+1) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // <SQL here>
    }
}
```

Also:
- Bump `version = N` → `version = N+1` in the `@Database` annotation
- Add `MIGRATION_N_N1` to the `.addMigrations(...)` call

## Step 4 — Remind about schema export

After editing AppDatabase.kt, remind the user:

> Run `./gradlew :core-data:exportRoomSchemas` (or trigger a debug build) to regenerate the schema JSON in `core-data/schemas/`. Commit the new JSON alongside the migration.

## Critical gotchas

- **Never add `alias(libs.plugins.kotlin.android)` to `:core-data` build file** — AGP 9.x auto-applies KGP; adding it manually causes a "Cannot add extension with name 'kotlin'" crash.
- The schema JSON filename will be `com.github.maskedkunisquat.wulfpak.core.data.AppDatabase/<N+1>.json` — verify it appears after the build.
- If the migration drops or renames a column, SQLite doesn't support `DROP COLUMN` before API 35 — use the copy-rename-drop pattern instead.