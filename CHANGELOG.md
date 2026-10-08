# Changelog

## 1.2.0 - 2026-10-08

### Changed
- Home names are shown exactly as typed. Before, MiniMessage tags and `&` colour codes in a name
  were carried out: a player could hide a click action in a home name (for example one that runs
  `/stop`), which ran as whoever clicked it in `/homes`, `/home` or `/ha list`. Names that used
  colours now show the tags as text.
- A `/home` during a running warmup replaces it: the countdown starts again for the new home.
  Before, the first home won and the second `/home` was cancelled with "you moved".

### Fixed
- `fluxhomes.homes.<number>` above 100 now counts (e.g. `fluxhomes.homes.150`). Before, only 1 to
  100 were checked, so such a player got `max-homes.default`. The limit is now read from the
  player's permissions in one pass instead of up to 100 checks.
- The cooldown message rounds up, so it never says "wait 0 seconds".
- An expired `/delhome` timer no longer cancels a newer confirmation of the same home early.

## 1.1.0 - 2026-10-07

### Added
- `table-prefix` setting: the table becomes `<prefix>homes`, so FluxHomes can share a MySQL
  database with other plugins. The default (empty) keeps the table `homes`.

### Changed
- Database connections come from a HikariCP pool, and all database work runs on one background
  thread. Homes are loaded while a player logs in; commands, tab completion and respawning read
  them from memory instead of waiting for the database.
- If the database cannot be reached at start-up, FluxHomes now disables itself with a clear message
  instead of failing on every command.
- `fluxhomes.homes.unlimited` now wins over numbered limits, so operators get unlimited homes.
- Home names longer than 32 characters are refused with a message (MySQL could not store them).
- The `version` in `plugin.yml` now always matches the release.
- `api-version` is `1.21` again, so FluxHomes loads on Paper 1.21.x as well as 26.x.

### Fixed
- `/homesadmin import essentialsx` works with current EssentialsX files (the world is stored as a
  UUID with `world-name`); imported names are lowercased so `/home` finds them.
- `/homesadmin tp` to a home in a world that is not loaded answers "world not found" instead of an
  error.
- MySQL: a connection closed by the server after a quiet period no longer loses the next save.
