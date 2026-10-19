# ATOMOS for Wear OS

The watch companion to ATOMOS: Nu's games on the wrist, and a collection of atoms you build by
playing them. It works on its own, with no phone and no sync, and it is free.

- **The look** is Quiet, grouped: calm on black, with each part of a screen on its own soft
  plate and room between (Home's sections, each game in the list, each part of an atom's page);
  the thing to tap first a disc (rose for Build and Next, white for Play and Resume, red to
  confirm a reset), and a word under each button where a row of them needs reading (the run's
  menu and cards, the confirm); numbers as the app's tiny atoms; one accent a screen. Lists shrink and fade toward the edges, with Wear's scroll arc on the right;
  the screens that don't scroll are laid out on a face 240 across and scaled to the watch.
- **Home** is a list the crown scrolls, the mark and ATOMOS, then four plates and Settings:
  - Build: the atom you are building as its cloud (filling with color shell by shell) with the
    rose Build disc in the middle, its name and its shells as a tiny atom (a tap opens its Build
    page), the next shell's game and difficulty, and Collection.
  - Atlas: the atom of the day (the same one the phone shows) as its plate, name and "Atom of
    the day", and All atoms.
  - Games: the game Nu picked for today (its white disc plays it, its dots play a difficulty),
    and All games.
  - Nu asks: Nu's three questions of the day (the same ones the phone asks).
  - Settings, a gear on a dark disc.
- **The games list** has every game, A to Z, each on its own plate: its icon, its name (Nu's pick with a rose dot
  after it), and under the name its three difficulties, Easy, Medium and Hard, as dots in green,
  yellow and red. A dot with a best holds it in black; one without is an empty ring. A tap on a
  dot plays that difficulty at once; a tap anywhere else on the row plays the one last played
  (Easy to start).
- **Build** shows every atom three ways, picked with three bare icons at the foot (the choice
  lasts until the app closes):
  - the table: every atom as a dot in its place, built ones lit in their own color, the one
    being built ringed in rose, and how many are built at the top;
  - the dial: the atoms as plates round the rim, the crown turning them past the top, and the
    picked atom's cloud, name and state in the middle;
  - the shells: the whole table as one atom, a shell of dots for each period, the picked atom's
    plate in the middle.

  In all three the crown moves a pick, a tap on an atom picks it, and a tap on the picked one
  opens it (on the table the picked atom's name replaces the count). Each atom's Build page fits
  one screen: its shells as the bezel (built ones white, the next rose), its number, symbol and
  name, then the rose disc with the next shell's game, which plays it, and its difficulty (once
  built, its cloud; before then a lock and where it stands), and the Atlas's book at the foot.
  The crown steps to the atoms either side; back is a chevron at the middle of the left edge.
- **The Atlas** is a wheel: one atom named, its symbol large in its color with its number and
  name, its neighbors' symbols either side, and the atoms round the rim as dots, the crown
  turning them. A bare icon at the foot switches to the table and back. A tap on the atom opens
  its Atlas page, a list: its symbol and name, where it sits, Build, the atom itself, the
  headline on a plate, the little table, then titled plates: Properties (each a slider, a track
  from the lowest atom to the highest, this one a dot in its color), Facts, Structure,
  Discovered (with where its name comes from), Isotopes, Made in and Used for; and the atoms
  either side.
- **A run** opens on a card: the game's icon in a ring that fills, in the difficulty's color,
  until play starts; its name and difficulty; and the levels to reach (an empty tiny atom and
  "Reach 3 levels"), or Free play (a tap or a turn of the crown starts it sooner). The ATOMOS
  mark, dim at the top of the screen (or the back button), pauses it: the game's icon and name
  and its difficulty; Exit, the big white Resume and Restart, each named under it; and how far
  the run has come ("Level 2 of 5", or the level and the best in free play). It ends on a card
  with what it built or reached, and Exit beside the disc that goes on, each named: the rose
  Next after a shell is built, else Again. The
  levels show as a tiny atom at the bottom of the screen; a game that keeps time shows the time
  left as a small ring round it, gray, then amber in the last quarter (no game draws a countdown
  round the whole bezel). Exit from free play goes back to the games list (or Home, for Nu's
  pick).
  - A run for an atom has a goal: 3 levels at Easy, 4 at Medium, 5 at Hard. Each level cleared
    fills one electron slot. Win, and the atom's next shell is built.
  - Free play has no goal. The tiny atom counts levels in binary across 8 slots, up to 255, and
    every level is harder than the last. Its difficulty is the dot tapped in the games list.
- **Settings** has Sound (off to start) and Haptics (on to start, and soft), each its mark,
  its name and a switch on one plate, and starting the collection over (it asks first: Cancel,
  or the red Yes, each named under its disc), with the version under them.
- **The tile** and **the Play with Nu complication** open straight into the next run for the
  atom. **The building complication** shows the atom being built on a watch face.

Atom Z has as many shells as its period: hydrogen takes 1 win, carbon 2, iron 4 and oganesson
7, so the whole table takes 620. Each shell is played at the atom's difficulty (`atomDial`, the
phone's curve moved up into the watch's three levels: 41 for hydrogen up to 88 for oganesson),
a little harder for each shell further out, up to 100 for oganesson's seventh.

The difficulty is the phone's dial, 1 to 100, seen in three levels: Easy (41 to 60), Medium (61
to 80) and Hard (81 to 100), the phone's Medium, Hard and Expert. Free play plays each at its
middle, 50, 70 and 90.

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

- `core/` is plain Kotlin with no Android in it: the game kit, the 32 games, the collection, the
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
| `atomos.levels.v2` | `{"<game>.<difficulty 0..2>": best level in free play}` (0 Easy, 1 Medium, 2 Hard) |
| `atomos.levels.v1` | The five-level watch's `{"<game>.<difficulty 0..4>": best}`: read once, moved to v2 (its 2, 3, 4 become 0, 1, 2; bests at 0 and 1 are dropped) |
| `atomos.picked.v2` | `{"<game>": difficulty 0..2 last picked for free play}` |
| `atomos.picked.v1` | The five-level watch's `{"<game>": difficulty 0..4}`: read once, moved to v2 (0 and 1 become 0) |
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
