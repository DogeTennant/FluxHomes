# FluxHomes

A modern, feature-rich homes plugin for Paper/Spigot/Purpur servers. Clean code, translation support, and a settings GUI included out of the box.

## Features

- Set, delete, and teleport to named homes
- Multiple homes per player with configurable limits
- Permission-based home limits (e.g. `fluxhomes.homes.5`)
- Teleport warmup with movement cancellation
- Teleport cooldown
- Enderman teleport sound (toggleable)
- Respawn at home on death (toggleable)
- Deletion confirmation to prevent accidents (toggleable)
- Per-world home blocking
- SQLite (default) and MySQL/MariaDB support
- Full translation support with bundled English and Czech (cs_cz)
- In-game settings GUI accessible via `/homesadmin settings`
- Import homes from EssentialsX
- MiniMessage formatting support (legacy `&` color codes also supported)
- Auto-updates config and translation files with missing keys on startup

## Requirements

- Paper or Spigot 1.21+
- Java 21+

## Installation

1. Download the latest release from the [Releases](../../releases) page
2. Drop the jar into your `plugins/` folder
3. Restart the server
4. Edit `plugins/FluxHomes/config.yml` to your liking

## Commands

### Player Commands

| Command | Description |
|---|---|
| `/home [name]` | Teleport to a home (defaults to "home") |
| `/sethome [name]` | Set a home at your current location |
| `/delhome <name>` | Delete a home |
| `/homes` | List all your homes |
| `/homes help` | Show command help |

### Admin Commands

| Command | Description |
|---|---|
| `/homesadmin delhome <player> <home>` | Delete a player's home |
| `/homesadmin listhomes <player>` | List a player's homes |
| `/homesadmin clearhomes <player>` | Clear all homes for a player |
| `/homesadmin settings` | Open the settings GUI |
| `/homesadmin language [name]` | View or change the active language |
| `/homesadmin import <source>` | Import homes from another plugin |
| `/homesadmin reload` | Reload config and translations |

**Supported import sources:** `essentialsx`

## Permissions

| Permission | Description | Default |
|---|---|---|
| `fluxhomes.home` | Teleport to a home | true |
| `fluxhomes.sethome` | Set a home | true |
| `fluxhomes.delhome` | Delete a home | true |
| `fluxhomes.homes` | List homes | true |
| `fluxhomes.admin` | Access admin commands | op |
| `fluxhomes.homes.unlimited` | Unlimited homes | op |
| `fluxhomes.homes.<number>` | Set a specific home limit (e.g. `fluxhomes.homes.5`) | false |

## Configuration

```yaml
# Storage type: sqlite or mysql
storage-type: sqlite

# Language file to use from the translations folder (e.g. en_us, cs_cz)
language: en_us

# Maximum homes for players without a specific permission
max-homes:
  default: 3

# Cooldown between teleports
cooldown:
  enabled: true
  seconds: 5

# Warmup before teleporting (player must stand still)
warmup:
  enabled: true
  seconds: 3

# Play enderman teleport sound on home teleport
teleport-sound:
  enabled: true

# Respawn players at their home named "home" on death
respawn-at-home:
  enabled: false

# Require players to confirm home deletion by running the command twice
confirm-deletion:
  enabled: true
  timeout: 30

# Worlds where players are not allowed to set homes
blocked-worlds:
  enabled: false
  worlds:
    - world_example
    - resource_world

# MySQL/MariaDB settings (only used if storage-type is mysql)
mysql:
  host: localhost
  port: 3306
  database: minecraft
  username: root
  password: ''
```

## MySQL and MariaDB

Set `storage-type: mysql` in `config.yml` and fill in your database credentials. MariaDB is fully supported since it uses the same connector as MySQL.

## Translations

Translation files are stored in `plugins/FluxHomes/translations/`. The plugin ships with:
- `en_us.yml` (English)
- `cs_cz.yml` (Czech)

To change the language, set the `language` option in `config.yml` or use `/homesadmin language <name>`.

To add a custom translation, create a new `.yml` file in the translations folder using `en_us.yml` as a template, then set the language in config to match the filename (without `.yml`).

Both MiniMessage tags (e.g. `<red>`, `<bold>`, `<gradient:red:blue>`) and legacy `&` color codes are supported in translation files.

## Building from Source

```bash
git clone https://github.com/DogeTennant/FluxHomes.git
cd FluxHomes
mvn package
```

The compiled jar will be in the `target/` folder.

## License

This project is open source under the [MIT License](LICENSE).
