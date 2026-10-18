# ATOMOS for Wear OS

The watch companion to ATOMOS: Nu's games on the wrist, and a collection of atoms you build by
playing them. It works on its own, with no phone and no sync, and it is free.

- **Home** shows the atom you are building. Its cloud fills with color as electrons come in, and
  the rim shows how far it has come. The rose Next button plays the game Nu wants to play. Turn
  the crown to look through your collection, and tap an atom to read about it.
- **Games** lists all 17 games, A to Z, with your best in each.
- **Settings** has Sound (off to start), Haptics (on to start, and soft), and starting the
  collection over.
- **The tile** shows Nu's pick with a Play button. **The complication** shows the atom being built
  on a watch face.

Every run pays electrons into the collection: 10 for a run at par, at least 1 for any score, and
double for a new best. Atom Z takes Z electrons, so the whole table takes 7,021.

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

- `core/` is plain Kotlin with no Android in it: the game kit, the 17 games, the collection, the
  atom clouds and the drawn marks. It is tested on any machine with
  `gradle -p core test` (or `gradlew -p core test`).
  `RENDER=1 gradle -p core test` also draws every game and cloud into `core/build/render/*.svg`.
- `app/` is the Wear OS app: Compose screens, the tile and the complication, haptics and sound,
  and the store.
- `app/src/main/assets/atoms.json` and `atoms.bin` are the 118 atoms and their clouds, baked from
  the phone app's own orbital sampler. To bake them again, from a checkout of the phone app:
  `npx -y tsx ../atomos-watch/tools/gen-atoms.mts . ../atomos-watch/app/src/main/assets`.
- `core/.../Quiz.kt` deals Nu's daily questions as the phone does. `QuizTest` checks it against
  the phone's own deal in `core/src/test/resources/quiz-phone.json`, made with
  `npx -y tsx ../atomos-watch/tools/gen-quiz.mts . <out file> [from day] [to day]`.

## Players' saved data

The watch keeps everything in one SharedPreferences file, `atomos`:

| Key | What it holds |
| --- | --- |
| `atomos.collection.v1` | `{building, electrons, built: {z: day}}` |
| `atomos.bests.v1` | `{gameId: best}`, in each game's own count |
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
