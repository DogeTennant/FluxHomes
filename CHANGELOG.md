# Changelog

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
