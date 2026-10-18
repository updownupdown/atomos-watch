# ATOMOS for Wear OS

- No GitHub Actions workflows. The owner's Actions minutes are limited; test locally.
- `core/` must stay free of Android so `gradle -p core test` runs anywhere. Run it before
  every push.
- Every game must end when ignored, and must never restart itself; the shell's Again does that.
  `GamesTest.everyGameEndsWhenIgnored` checks this.
- Saved data (see README, "Players' saved data"): never rename a key, reshape its JSON or rename
  a game id without a migration.
- Style follows the phone app: pure black, flat and luminous, Outfit, cyan accent, rose only for
  Next and Nu, scores as tiny atoms, no instruction text in games, no dashed lines.
- Sound is off by default. Haptics are on by default and must stay subtle.
