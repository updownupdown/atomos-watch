# ATOMOS for Wear OS

The watch companion to ATOMOS: Nu's games on the wrist, and a collection of atoms you build by
playing them. It works on its own, with no phone and no sync, and it is free.

- **Home** is a short list the crown scrolls, in sections:
  - **Build**: the atom you are building (its cloud fills with color shell by shell, the game
    and difficulty of the next shell, and the rose Build button), and your collection.
  - **Atlas**: the atom of the day (the same one the phone shows) and every atom.
  - **Games**: the game Nu picked for today, Nu's three questions of the day (the same ones the
    phone asks), and Play all games.
  - The Settings button.
- **Build** shows the atom being built and every period's atoms, built ones in their own color.
  Each atom's Build page has its cloud, View in Atlas, and its shells as a track: built ones
  checked, the next with its game, difficulty and Play.
- **The Atlas** shows every atom on a wheel (the crown steps through them) or in the table. Each
  atom's Atlas page has its plate, Build, the atom itself, the headline and facts, its name and
  discovery, its structure, properties and isotopes, and where it comes from and what it is for,
  each a section that folds (only the facts open to start).
- **A run** opens on a card with the game's name, its difficulty and the levels to reach, and a
  ring round its icon that fills until play starts (a tap or a turn of the crown starts it
  sooner). The ATOMOS mark at the top of the screen (or the back button) pauses it: Exit on the
  left, Resume in the middle, Restart on the right, with the level reached and the best at the
  foot. The levels show as a tiny atom at the bottom of the screen.
  - A run for an atom has a goal: 3 levels at Very easy up to 7 at Expert. Each level cleared
    fills one electron slot. Win, and the atom's next shell is built.
  - Free play has no goal. The tiny atom counts levels in binary across 8 slots, up to 255, and
    every level is harder than the last. A game's page picks the difficulty: tap a dot or turn
    the crown.
- **Settings** has Sound (off to start), Haptics (on to start, and soft), and starting the
  collection over.
- **The tile** and **the Play with Nu complication** open straight into the next run for the
  atom. **The building complication** shows the atom being built on a watch face.

Atom Z has as many shells as its period: hydrogen takes 1 win, carbon 2, iron 4 and oganesson
7, so the whole table takes 620. Each shell is played at the atom's difficulty (the phone's
`atomDial`, 5 for hydrogen up to 75 for oganesson), a little harder for each shell further out.

## Run it on your watch

1. Open this folder in Android Studio and let it sync Gradle.
2. On the watch, turn on Developer options (Settings, System, About, tap the build number seven
   times). Then, in Developer options, turn on ADB debugging and Wireless debugging.
3. In Android Studio, open Device Manager, choose **Pair devices using Wi-Fi**, and pair with the
   code the watch shows under Wireless debugging. Or, from a terminal:
   `adb pair <ip>:<pairing port>`, then `adb connect <ip>:<port>`.
4. Pick the `app` run configuration and the watch, then press Run.

From a terminal: `gradlew :app:installDebug` builds and installs to the connected watch.

## Layout

- `core/` is plain Kotlin with no Android in it: the game kit, the 37 games, the collection, the
  atom clouds and the drawn marks. It is tested on any machine with
  `gradle -p core test` (or `gradlew -p core test`).
  `RENDER=1 gradle -p core test` also draws every game and cloud into `core/build/render/*.svg`.
- `app/` is the Wear OS app: Compose screens, the tile and the complication, haptics and sound,
  and the store.
- `app/src/main/assets/atoms.json` and `atoms.bin` are the 118 atoms and their clouds, baked from
  the phone app's own orbital sampler. To bake them again, from a checkout of the phone app:
  `npx -y tsx ../atomos-watch/tools/gen-atoms.mts . ../atomos-watch/app/src/main/assets`.
- `atoms-info.json` is what the Atlas tells about each atom, baked the same way with
  `tools/gen-info.mts`.
- `core/.../Quiz.kt` deals Nu's daily questions as the phone does. `QuizTest` checks it against
  the phone's own deal in `core/src/test/resources/quiz-phone.json`, made with
  `npx -y tsx ../atomos-watch/tools/gen-quiz.mts . <out file> [from day] [to day]`.

## Players' saved data

The watch keeps everything in one SharedPreferences file, `atomos`:

| Key | What it holds |
| --- | --- |
| `atomos.collection.v2` | `{building, shells, built: {z: day}}` |
| `atomos.collection.v1` | The first builds' `{building, electrons, built}`: read once, moved to v2 |
| `atomos.levels.v1` | `{"<game>.<difficulty 0..4>": best level in free play}` |
| `atomos.picked.v1` | `{"<game>": difficulty 0..4 last picked for free play}` |
| `atomos.quiz.v1` | `{day, answers: [bool], streak, streakDay}` |
| `atomos.sound` | Sound on or off (off to start) |
| `atomos.haptics` | Haptics on or off (on to start) |
| `atomos.runs` | Runs played, used to change Nu's pick after each run |

Game ids are part of the contract. Adding keys and games is safe. Never rename or reshape a key,
or rename a game id, without a migration.

## No CI

There are no GitHub Actions workflows, to keep Actions minutes free. Run the core tests and an
app build locally before merging.

## Fonts

Outfit, under the SIL Open Font License (`licenses/Outfit-OFL.txt`).
