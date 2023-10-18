![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/okocraft/PunishmentNotifier)
![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/okocraft/PunishmentNotifier/maven.yml?branch=master)
![GitHub](https://img.shields.io/github/license/okocraft/PunishmentNotifier)

# PunishmentNotifier

A Velocity plugin that sends notifications to the Discord when the player has been punished by LibertyBans.

## Features

- Send punishment/pardon logs to Discord using a webhook
- Send notifications when the player who is punished offline is logged in

## Requirements

- Java 17+
- Velocity
- LibertyBans

## How to install this plugin

- Download a plugin jar from [the Release page](https://github.com/okocraft/PunishmentNotifier/releases)
- Place the downloaded jar in the plugin directory (`plugins`)
- Set your webhook URL to `discord-webhook-url` in `config.yml`
    - `config.yml` is in the plugin directory (`./plugins/punishmentnotifier`)
- Run `/pnreload` to reload the plugin

## License

This project is licensed under the permissive GPL-3.0 license. Please see [LICENSE](LICENSE) for more info.

Copyright © 2020-2023, Siroshun09
