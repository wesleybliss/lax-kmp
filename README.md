# Lax (Kotlin Multiplatform)

A minimal native Slack desktop client, rewritten in **Kotlin Multiplatform** +
[Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/).
Desktop JVM targets (Linux first; Windows and macOS packaging included).

Personal use: one human, one install, no automation.

## License

[PolyForm Noncommercial 1.0.0](LICENSE): free to use, modify and share, including
the source, for any noncommercial purpose. You may not sell it or anything built
with it.

## Run

```sh
./run.sh
./run.sh --team "My Workspace" --channel C0123456789
```

Hot reload: `./gradlew :desktopApp:hotRun --auto`

`LAX_DEBUG=1` logs connection noise to stdout (never content, tokens, or cookies).

## What this reads from disk, and why

There is no OAuth flow and no registered Slack app. Lax reuses the session the
**Slack desktop app already has**, like wee-slack and slk do.

On Linux startup it reads, from a temporary copy of Slack’s profile:

- `Local Storage/leveldb/` → `xoxc-` tokens (`localConfig_v2`)
- `Cookies` (sqlite) → encrypted `d` cookie
- Secret Service (`secret-tool`) → Chromium “Slack Safe Storage” password

Profile paths tried: `$XDG_CONFIG_HOME/Slack`, `~/.config/Slack`, Flatpak, Snap.
Tokens and cookies stay in memory (and optionally your keyring) and are never logged.

**This is unsupported by Slack and may break or violate their terms. You accept that risk.**

If desktop extraction fails, Lax prompts for a pasted workspace URL, token, and cookie.

## Layout

- `shared/` — models, store, Slack protocol, Compose UI (common + JVM)
- `desktopApp/` — Compose Desktop entrypoint and native packaging
- `shared/.../slack/desktop/` — Linux credential extraction (LevelDB, cookie decrypt, keyring)

## Status (port)

Working toward parity with the Zig client: workspaces rail, sidebar, message list,
composer, live websocket, send/edit/delete/reactions plumbing, mrkdwn, themes
shell, settings/layout persistence, manual credential fallback.

Still thinner than the Zig build: feeds pages, full hover toolbar/emoji picker,
file drop/upload, image cache viewer, notifications, and theme JSON loading are
stubbed or incomplete.

## Requirements

- JDK 25 (system OpenJDK is fine; Gradle toolchain + daemon use 25)
- Linux: `secret-tool` (libsecret) for cookie decrypt / saved session
- Signed-in Slack desktop app, or pasted credentials
