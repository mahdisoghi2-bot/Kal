# کال (Kāl) — Android, Compose Foundation only

**Status: source project, not yet compiled.** It was written in an environment with no Android SDK or network,
so it has never been built or run. Expect to fix a few compile errors on first build (Compose API details are the
most likely culprits). Everything below is designed so that is a quick loop.

## Get an APK

### A. GitHub Actions (no installs)
1. Create a GitHub repo, push this folder.
2. Actions → **Build APK** → Run workflow. Download the `kal-apks` artifact (debug, release per-ABI, release universal).
3. Without secrets the release APK is signed with a throwaway key (installs fine, but you can't update it later with a different key).
   For a real key, generate one (below) and add repo secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`.

### B. Android Studio
File → Open → this folder → let it sync → Build → Build APK(s). Uses JDK 17 (the bundled JBR works).

### C. Command line (JDK 17 + Android SDK platform 35 + Gradle 8.9)
```
gradle wrapper --gradle-version 8.9      # one time; creates ./gradlew
./gradlew :core:test
./gradlew :app:assembleDebug             # app/build/outputs/apk/debug/
./gradlew :app:assembleRelease           # app/build/outputs/apk/release/ (arm64-v8a, armeabi-v7a, universal)
```

### Keystore
```
keytool -genkeypair -v -keystore kal-release.jks -alias kal -keyalg RSA -keysize 2048 -validity 10000
```
Create `keystore.properties` next to `settings.gradle.kts` (git-ignored):
```
storeFile=kal-release.jks
storePassword=...
keyAlias=kal
keyPassword=...
```
Without it, release builds are unsigned (not installable).

## Adding the food model (important)
No model is bundled (none could be downloaded here). Until you add one, the app runs a **DemoAnalyzer**: it finds a
region by colour/saturation and picks a dish from `foods.json` by hue. **Those results are not real recognition.**

To use a real model, drop into `app/src/main/assets/`:
- `food_classifier.tflite` — image classifier, input `[1,H,W,3]` (uint8, or float32 normalised to −1..1), output `[1,N]`
- `food_labels.txt` — one label per line, same order as the output (e.g. Food-101 labels)
- `food_detector.tflite` *(optional)* — standard TFLite SSD detector (boxes/classes/scores/count); otherwise the box comes from the colour-based proposer

Labels map to Persian names and kcal through `foods.json` (`id` or `aliases`). Unknown labels are ignored.
Put an edited `foods.json` in the app's `files/` dir to override the bundled one.
To use a different model or a remote API, implement `FoodAnalyzer` and return it from `KalViewModel`'s init. The UI doesn't change.

## Placeholders to replace
- Logo: `res/drawable/ic_logo_foreground.xml`, `ic_logo_mono.xml`, `ic_splash.xml`, `ic_splash_vector.xml` (all marked `PLACEHOLDER_*`).

## Layout
- `core/` UI-free Kotlin: Persian formatter, ripening colour, calorie/portion/wedge maths, day rollover (04:00), tests.
- `app/` Compose Foundation UI, CameraX, DataStore, TFLite. `ui/Tokens.kt` mirrors `tokens.json` for the web version.
- Fonts: Peyda (all 7 weights) for text, Radio Fill for the hero/display numerals. Radio Outline is not bundled.

## Decisions where the spec was silent or I deviated
- **Opening the result:** tap anywhere while the box is locked (no shutter). First 3 uses show a small caption under the box.
- **Sheet:** uses `draggable` + animated snap instead of `AnchoredDraggableState` (that API changed between Compose versions).
- **Progress → camera** is a slide/fade, not the shared-element morph. Camera → Result is the full squircle morph with predictive-back scrubbing.
- Peyda has no Bold; hero/display use Radio Fill's single weight, title uses Medium.
- Portion estimate: base kcal × (box area ÷ 30% of frame), clamped 0.6–1.6×, rounded to 5.
