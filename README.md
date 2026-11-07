# Atomos for Wear OS

**Quick mini-games on your wrist that build a collection of all 118 atoms, shell by shell.**

This is the watch companion to [Atomos](https://github.com/updownupdown/atomos) ([buildatomos.app](https://buildatomos.app)). It runs entirely on its own, with no phone or account needed. Play any of 32 short games, chosen daily by Nu, the app's neutrino guide; each run you win builds the next electron shell of the atom you're working on, from hydrogen (one shell) all the way to oganesson (seven). Atoms show as their real probability clouds, an Atlas covers every element's properties, facts, isotopes and discovery, and Nu asks three science questions a day (the same ones as on the phone). A tile and two watch-face complications drop you straight into your next run.

## Built with

Kotlin and Compose for Wear OS. The game logic, all 32 games and the atom clouds live in a plain-Kotlin `core` module with no Android dependency, so it's unit-tested on any machine and can render every game to SVG for review. Atom data and clouds are baked from the phone app's own orbital sampler, so both apps show exactly the same atoms.

## Install it on your watch (sideload)

You'll need a watch running Wear OS 3 or later, and `adb` on your computer (it comes with [Android's platform tools](https://developer.android.com/tools/releases/platform-tools)).

1. **Download** the latest `atomos-watch-….apk` from [Releases](https://github.com/updownupdown/atomos-watch/releases/latest).
2. **Turn on developer mode on the watch:** Settings → System → About → Versions, then tap **Build number** seven times.
3. **Turn on debugging:** Settings → Developer options → enable **ADB debugging** and **Wireless debugging**. Keep the watch and your computer on the same Wi-Fi.
4. **Connect:** in Wireless debugging, tap **Pair new device**, then run these with the IP, ports and code the watch shows:
   ```sh
   adb pair <ip>:<pairing-port>
   adb connect <ip>:<port>
   ```
5. **Install:**
   ```sh
   adb install atomos-watch-<version>.apk
   ```

The app appears in the watch's app list. To update, install a newer APK the same way; your progress is kept.

Tips: if the watch drops off Wi-Fi, put it on its charger while installing. After a restart, run `adb connect` again (the port changes). You can turn Wireless debugging off again afterwards.

Or build it yourself: clone the repo and run `./gradlew :app:installDebug`, or open the folder in Android Studio, pick the `app` configuration and press Run.

## Development

```sh
./gradlew -p core test             # core rules and games
RENDER=1 ./gradlew -p core test    # also draws every game and cloud to core/build/render/*.svg
```

Screen-by-screen design notes, the data pipeline and the saved-data format are in [docs/DESIGN.md](docs/DESIGN.md). There's no CI; run the core tests and an app build before merging. To publish a sideload release, bump `versionName` in `app/build.gradle.kts` and run **Actions → Release APK → Run workflow**; it builds a signed APK and attaches it to a GitHub Release.

Font: Outfit, under the SIL Open Font License.
