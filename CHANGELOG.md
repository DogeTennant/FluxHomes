# Changelog

## 1.2.3 - 2026-10-17

### Fixed
- The settings menu can no longer be used from a chest named "FluxHomes Settings". Before, any
  player could rename a chest in an anvil and change every setting by clicking items in it - the
  home limit for everyone, the cooldown, the warmup, blocked worlds. The menu is now recognised by
  its own marker, and every click needs `fluxhomes.admin`.
- A click on an item in your own inventory below the settings menu no longer changes a setting.
- A teleport another plugin stops (combat, regions) now says "The teleport to base was stopped."
  with no cooldown. Before, it said "Teleported to base." and the cooldown started.
- The blocked-worlds chat prompt ends when the admin leaves. Before, their next chat message, even
  after rejoining, was hidden and added as a blocked world.
- `respawn-at-home` only applies after a death. Before, leaving the End through the exit portal
  also took players to their home.

### Changed
- `/homesadmin reload` says when the storage settings (`storage-type`, `table-prefix`, `mysql`)
  changed: they take effect after a restart.

## 1.2.2 - 2026-10-16

### Fixed
- A YAML error in `config.yml` (one missing bracket or quote) keeps FluxHomes off at start, with a
  console message. Before, the whole file was replaced by the built-in one - the MySQL login and
  table prefix included - and FluxHomes ran on SQLite, so every home looked gone.
- A YAML error in a translation no longer gets the file replaced by the English texts; it is left
  alone, and the texts from before (at a start the built-in ones) are used until it is fixed.
- `/homesadmin reload` with such an error keeps what was read before and says which file has it.
- The settings menu and `/homesadmin language` change only their own option in `config.yml`. Before,
  they saved the whole config in use: hand edits not reloaded yet were lost, and after a YAML error
  the file was overwritten. While the file has an error they now change nothing and say so.

## 1.2.1 - 2026-10-08

### Fixed
- Backslashes in home names are shown too. Before, a name ending in `\` showed the message's own
  formatting as text ("Home base</white> has been set.").

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
