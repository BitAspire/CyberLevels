# CyberLevels - Server Leveling System

[![CI](https://github.com/BitAspire/CyberLevels/actions/workflows/build.yml/badge.svg)](https://github.com/BitAspire/CyberLevels/actions/workflows/build.yml)
[![Latest Release](https://img.shields.io/github/v/release/BitAspire/CyberLevels?sort=semver)](https://github.com/BitAspire/CyberLevels/releases)
[![License](https://img.shields.io/github/license/BitAspire/CyberLevels)](LICENSE)
[![Java 8+](https://img.shields.io/badge/Java-8%2B-orange)](https://www.oracle.com/java/technologies/javase/8all-relnotes.html)
[![Gradle](https://img.shields.io/badge/Gradle-9.7.1-02303A)](https://gradle.org/)

A leveling system plugin for **Spigot** and **Paper** with **Folia support**. Store player levels and experience, reward activity across your server, sync progress through SQL storage, and show progression through chat, action bar, PlaceholderAPI, leaderboards, and more.

---

## 🔗 Downloads
Download our plugin only from these sources:
- [SpigotMC](https://www.spigotmc.org/resources/cyberlevels.98826/)
- [Releases](https://github.com/BitAspire/CyberLevels/releases/)
---

## 🧩 Supported versions

- **Minecraft:** 1.8+ (check the resource page for the latest tested builds).
- **Native / primary:** 1.16+ workflow, with legacy dependency loading for older servers when needed.
- **Java:** 8+ (newer Java versions supported).
- **Platforms:** Spigot, Paper, and Folia-supported server setups.

---

## ✨ Features

- 💾 **Flexible storage** - Flat-file storage, MySQL, MariaDB, SQLite, PostgreSQL, and H2. SQLite works out of the box when SQL storage is enabled with the default type.
- 🔁 **Shared database syncing** - Useful for multi-server networks that share the same database table.
- 🧮 **Custom EXP requirements** - Use a general formula or per-level formulas in `levels.yml`.
- 🔢 **Large-number mode** - Optional BigDecimal calculations for servers that use very large EXP values.
- 🎁 **Level rewards** - Run player or console commands, send chat/action bar/title/global messages, play sounds, and control duplicate reward claims.
- ⚡ **Earn EXP events** - Configure EXP from timed rewards, deaths, damage, kills, placing, breaking, consuming, moving, crafting, brewing, enchanting, fishing, breeding, chat, and vanilla EXP gains.
- 🧱 **Block-state keys** - Reward precise block states for crops and other blocks where server versions expose that data.
- 🛡️ **Anti-abuse controls** - Natural-block checks, silk-touch behavior, crop maturity checks, cooldowns, limiters, timers, and world filters.
- 📈 **Leaderboard** - Configurable `/clv top` rankings with PlaceholderAPI leaderboard slots.
- 🚀 **Multipliers** - Permission-based EXP multipliers, plus external booster support when AxBoosters is installed.
- 🔌 **Plugin hooks** - PlaceholderAPI, RivalHarvesterHoes, RivalPickaxes, AxBoosters, AxHoes, and AxPickaxes hooks load when present.
- 🧰 **Auto-updating configs** - New `config.yml`, `lang.yml`, and `earn-exp.yml` keys can be merged from defaults on update.
- 🔔 **Spigot update notice** - Optional async startup check with operator chat notifications.

**Soft-dependencies declared by the plugin:** PlaceholderAPI, Vault, CyberWorldReset, RivalHarvesterHoes, RivalPickaxes, AxBoosters, AxHoes, AxPickaxes.

---

## 📦 Installation

1. Download the latest jar from [SpigotMC](https://www.spigotmc.org/resources/cyberlevels.98826/).
2. Place it in your server's `plugins` folder.
3. Start the server once to generate `config.yml`, `lang.yml`, `levels.yml`, `earn-exp.yml`, `rewards.yml`, and `anti-abuse.yml`.
4. Edit configs as needed and run `/clv reload` with admin permission.

For database setup, use the `config.mysql` section in `config.yml`. Set `enabled: false` for flat-file storage, or keep it enabled and choose one of `MYSQL`, `MARIADB`, `SQLITE`, `POSTGRES`, `POSTGRESQL`, or `H2`.

Full documentation: [CyberLevels Wiki](https://wiki.bitaspire.com/en/docs/cyberlevels)

---

## 🕹️ Commands

Main command: **`/clv`** (aliases: `cyberlevels`, `cyberlevel`, `cyberlvl`, `levels`, `level`, `lvl`, `lvls`).

| Command | Description |
| --- | --- |
| `/clv` | Show your level / EXP progress |
| `/clv about` | About the plugin |
| `/clv help` | Help menu |
| `/clv reload` | Reload configuration *(admin)* |
| `/clv top` | Leaderboard |
| `/clv info [player]` | Level / EXP progress |
| `/clv addExp <amount> [player]` | Add EXP |
| `/clv setExp <amount> [player]` | Set EXP |
| `/clv removeExp <amount> [player]` | Remove EXP |
| `/clv addLevel <amount> [player]` | Add levels |
| `/clv setLevel <amount> [player]` | Set level |
| `/clv removeLevel <amount> [player]` | Remove levels |
| `/clv purge <player>` | Remove a player from CyberLevels data *(admin)* |

---

## 🔐 Permissions

**Players**

| Permission | Purpose |
| --- | --- |
| `CyberLevels.player.*` | All player permissions |
| `CyberLevels.player.help` | Help menu |
| `CyberLevels.player.info` | Own level / EXP info |
| `CyberLevels.player.about` | Plugin about |
| `CyberLevels.player.top` | View leaderboard |
| `CyberLevels.player.multiplier.<amount>` | EXP multiplier, for example `CyberLevels.player.multiplier.2` |
| `cyberlevels.suppress.levelup.player` | Suppress personal level-up reward messages |
| `cyberlevels.suppress.levelup.global` | Suppress global level-up reward messages |
| `cyberlevels.suppress.levelup.*` | Suppress all level-up reward messages |

**Admins**

| Permission | Purpose |
| --- | --- |
| `CyberLevels.admin.*` | All admin permissions |
| `CyberLevels.admin.help` | Admin help |
| `CyberLevels.admin.info` | View others' info |
| `CyberLevels.admin.reload` | `/clv reload` |
| `CyberLevels.admin.purge` | `/clv purge <player>` |
| `CyberLevels.admin.exp.*` | add/set/remove EXP |
| `CyberLevels.admin.exp.add` | Add EXP |
| `CyberLevels.admin.exp.set` | Set EXP |
| `CyberLevels.admin.exp.remove` | Remove EXP |
| `CyberLevels.admin.levels.*` | add/set/remove level |
| `CyberLevels.admin.levels.add` | Add level |
| `CyberLevels.admin.levels.set` | Set level |
| `CyberLevels.admin.levels.remove` | Remove level |

---

## 🧪 PlaceholderAPI

Prefix: **`%clv_`** ... **`%`**

### 🏆 Leaderboard

Use **`leaderboard_<type>_<position>`** where:

- **type:** `name`, `displayname`, `level`, or `exp`
- **position:** `1` ... up to your configured leaderboard size (`config.leaderboard.max-positions`)

Examples:

- `%clv_leaderboard_name_1%` - name at rank 1
- `%clv_leaderboard_level_3%` - level at rank 3
- `%clv_leaderboard_exp_5%` - EXP at rank 5

If the leaderboard is disabled, loading, or the position is out of range, the expansion returns the configured fallback text.

### 👤 Player

The player placeholders require an online player context.

| Placeholder | Description |
| --- | --- |
| `%clv_player_level%` | Current level |
| `%clv_player_level_next%` / `%clv_player_next_level%` | Next level, capped at max |
| `%clv_player_exp%` / `%clv_player_experience%` | Current EXP |
| `%clv_player_exp_required%` / `%clv_player_experience_required%` | EXP required for next level |
| `%clv_player_exp_remaining%` / `%clv_player_experience_remaining%` | EXP until level up |
| `%clv_player_exp_progress_bar%` / `%clv_player_experience_progress_bar%` | Progress bar |
| `%clv_player_exp_percent%` / `%clv_player_experience_percent%` | Progress percent |

### 🌍 Global

| Placeholder | Description |
| --- | --- |
| `%clv_level_maximum%` | Max level |
| `%clv_level_minimum%` | Min / start level |
| `%clv_exp_minimum%` / `%clv_experience_minimum%` | Starting EXP |

---

## 🧬 API (for plugin developers)

CyberLevels exposes a Bukkit event for plugins that want to inspect or adjust incoming EXP.

### 📚 Dependency

- Add **CyberLevels** as a **`compileOnly`** or provided dependency if you build against its API classes.
- Add **CyberLevels** under **`softdepend`** or `depend` in your `plugin.yml` so your plugin loads after CyberLevels at runtime.
- New integrations should use `com.bitaspire.cyberlevels.event.ExpChangeEvent`.
- The legacy `net.zerotoil.dev.cyberlevels.api.events.XPChangeEvent` is still present for compatibility, but it is deprecated.

### `ExpChangeEvent`

| | |
| --- | --- |
| **Class** | `com.bitaspire.cyberlevels.event.ExpChangeEvent` |
| **Fires when** | A player is about to gain CyberLevels EXP after permission and booster multipliers are applied. Only positive EXP gains from online users fire this event. |
| **Can modify** | `setExpAmount(double)` or `setAmount(double)` can change the EXP amount before CyberLevels applies it. |
| **Threading** | The event is async when the calling thread is not the server main thread. Handle both sync and async listeners as usual. |

**Methods**

| Method | Description |
| --- | --- |
| `getUser()` | `LevelUser<?>` receiving the EXP |
| `getPlayer()` | Online Bukkit player receiving the EXP |
| `getOldExp()` / `getNewExp()` | EXP before and after this gain preview |
| `getOldLevel()` / `getNewLevel()` | Level before and after this gain preview |
| `getExpAmount()` / `setExpAmount(double)` | EXP being added |
| `getAmount()` / `setAmount(double)` | Legacy-style aliases for the EXP amount |
| `getOldXP()` / `getNewXP()` | Legacy-style aliases for EXP values |

### Example

`plugin.yml`:

```yaml
name: MyPlugin
main: com.example.myplugin.MyPlugin
version: 1.0
softdepend: [CyberLevels]
```

Register a `Listener` in your plugin's `onEnable`:

```java
import com.bitaspire.cyberlevels.event.ExpChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("CyberLevels") == null) {
            getLogger().warning("CyberLevels not found; ExpChangeEvent will never fire.");
            return;
        }

        getServer().getPluginManager().registerEvents(new XpBoostListener(), this);
    }

    private static final class XpBoostListener implements Listener {

        @EventHandler(ignoreCancelled = false)
        public void onCyberLevelsExpChange(ExpChangeEvent event) {
            double boosted = event.getExpAmount() * 1.1;
            event.setExpAmount(boosted);
        }
    }
}
```

---

## 🤝 Support & contributing

- **Bugs / questions:** primarily use our [Discord server](https://discord.gg/DC4Gqj3y5V). We do not check GitHub issues that often, and Spigot reviews should not be used as support tickets.
- **Donations:** [PayPal: zerotoildev](https://paypal.me/zerotoildev)

### 📜 License

CyberLevels is a licensed plugin with our custom BCPL v1.0 license. Before using or contributing to our plugin, we recommend to read this document.

---

### 👋 Contributing

Contributions are welcome! Please select tab "Contributing" and read more information about how to contribute. We appreciate any help or bugfix from our community.

---

## 👥 Authors

**Kihsomray**, **CroaBeast**

## 🌟 Contributors

**Klema_LP**, **Faln**, **Zsomi**, **hermes18974-jpg**

&copy; BitAspire.com | Product of ZeroToil LLC
