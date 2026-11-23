# Gym

A training app for two partners who work out together. You build training plans, start a workout,
and both log weights and reps, your own or each other's. Everything syncs live between the two phones.

- **Stack:** Kotlin 2.4 Multiplatform + Compose Multiplatform 1.12, Android Gradle Plugin 9.
  - `composeApp`: the shared library with all the UI and logic (`src/commonMain`), so the iPhone
    version reuses it. The view model's self-contained parts (gyms, rest timer, your own
    exercises) live in `com.gym.app.state`.
  - `androidApp`: the thin Android app with MainActivity, Google sign-in, the photo picker, the rest
    alarm and notifications, the manifest, resources, `google-services.json` and the release settings.
  An iPhone app would be a second thin wrapper next to it.
- **Sync:** Firebase Firestore through the [GitLive Firebase Kotlin SDK](https://github.com/GitLiveApp/firebase-kotlin-sdk).
  It updates live and works offline. Changes made with no signal upload when you're back online.
- **Accounts:** Sign in with Google through Android's Credential Manager and Firebase Auth. Your pair
  and history follow your Google account to a new phone. The two of you pair up with a
  6-character code.

## How it works

1. You both sign in with Google.
2. Partner A taps **Start a new pair**, and the app shows a code.
3. Partner B enters that code (or taps **Share** to send it) and taps **Join my partner**.
4. Either of you creates plans, such as "Push day" with exercises and target sets × reps.
5. **Start workout** creates the sets for both of you. Weights are pre-filled from last time.
   Each exercise card has a column per person, and either phone can edit either column.
6. **Finish** saves it to History and remembers each person's working weights. Next time, each
   column shows a **"Last: 80×10 · 85×8"** line for that exercise, gym and machine.
7. The **⋮** menu on a workout can rename or discard it. The **⋮** on an exercise moves it up or
   down, or removes it. Removing a set or exercise, or discarding a running workout, happens
   straight away with an **Undo** in the snackbar. Once every set of an exercise is ticked, it
   shrinks to one line (tap it to open it again) and the next exercise scrolls into view. The screen
   stays on during a workout. The keyboard's **Next** key steps through kg, then reps, then the next set
   in your own column.
8. **Settings** (home **⋮**) is where you change your name, see your partner or pair code, leave
   the pair, manage gyms, set the rest timer and sign out. Signing out also wipes the workout data
   stored on the phone.
9. **Machine notes:** tap **Add note** under a name in a workout to save settings such as "seat 4,
   back pad 2". Notes are kept per person, exercise, gym and machine brand, and show every time.
10. **Rest timer:** ticking a set off starts a countdown bar at the bottom (−15s, +15s, Skip). The
    phone buzzes when it reaches zero. The length is set in Settings and can be switched off. With the
    app in the background or the phone locked, a countdown notification shows and the phone still
    buzzes on time. Allow notifications when asked, the first time a rest starts.
11. **Repeat:** "Repeat last" on Home, or **⋮ → Repeat this workout** on a past workout, starts a new
    workout with the same exercises, machine brands and types.
12. **Summary and progress:** a finished workout opens with a summary for each person (sets done,
    volume, and 🏆 new bests: a top set heavier than ever before on that exact gym, machine and type).
    The **Progress** tab on Home lists every exercise you've finished. Tap one for a chart of both
    people over time (one gym, machine and type at a time) and the session history. The chart can
    show the top set, an estimated 1-rep max (Epley) or volume, over 3 months, 1 year or everything;
    tap a point for that session's details.
13. **Plans:** each exercise's **⋮** has Swap, Move up, Move down and Remove. The copy icon on a plan
    card duplicates the plan.
14. **Default sets × reps** (Settings → New exercises, shared by the pair) is what every exercise starts
    with when you add it to a plan or workout.
15. **Your own exercises** can have a photo: **Take** (camera) or **Choose** (gallery) when you create
    one, or later from its details. Tap its picture in a workout or the picker. Photos are shrunk to
    about 800 px and stored in Firestore, so they sync without Firebase Storage. The details screen
    also lets you **Edit** your own exercise (a new name carries its plans, history, weights, notes
    and charts over) or **Delete** it.
16. **History** opens with the last 8 weeks at a glance (a square per day you trained, and each
    week's count), a search box (workout, exercise or gym name) and a gym filter. Search covers the
    workouts loaded so far; **Show older workouts** at the bottom loads more.
17. **Where's my partner:** during a workout, a line under the gym shows what your partner is on,
    e.g. "Graciela · Leg press, set 3 · just now", from the set they last changed. Tap it to jump there.
18. **Appearance** (Settings, per phone): follow the phone, or always light or dark, and optionally
    Android 12+ wallpaper colours instead of Gym's orange and teal.
19. **Warm-up and Mobility** (in a plan): two optional lists of moves, done as a circuit with a
    number of rounds (e.g. 4 mat exercises × 3 rounds). They're picked from a warm-up and
    mobility catalogue (cardio, dynamic moves, stretches and foam rolling, all with photos and
    how-to steps) or your own exercises. A workout shows the warm-up above the exercises and
    mobility after them; tap a move for its how-to. Nothing is logged for them. Past workouts
    keep the lists they had, and Repeat copies them. The catalogue is generated by
    `tools/make_mobility.py` from the same free-exercise-db as the main one.
20. **Screen readers:** every weight, reps and tick box says whose set it is, e.g. "Chris, set 2,
    weight in kilos".

## Fast build for your phones

The debug build is slow. For everyday use, install the optimised release build. It's code-shrunk
(about 4 MB instead of 29 MB), signed with the debug key so it installs over the debug app, and
Google sign-in keeps working:

```bash
./gradlew :androidApp:assembleRelease
adb install -r androidApp/build/outputs/apk/release/androidApp-release.apk
```

A Play Store upload would need its own signing key; that's still to be set up.

## Tests

```bash
./gradlew :composeApp:testAndroidHostTest
```

There are 62 unit tests. They cover the weight memory, personal bests, progress records, machine notes, repeating a workout (pre-fill, re-fill on gym, brand or type
changes, what's remembered at finish, and "Last time"), number parsing, ids and invite codes, and
the exercise catalogue (no duplicates, every photo has instructions, photo links pinned),
renaming your own exercises, and the chart values, History calendar, search and partner position.

### Testing the security rules

`tools/rules_test.py` checks `firestore.rules` against Google's local Firestore emulator, so a change
to the rules can't quietly open your data to others. It plays you, your partner, a stranger, someone
joining with a code, an anonymous sign-in and nobody signed in, and checks who can read or change
what: profiles, invite codes, joining and leaving a pair, and everything inside it. Python's standard
library is all it needs.

```bash
python3 tools/rules_test.py
```

It needs the emulator `.jar` in `tools/firestore-emulator/` (not in git, about 140 MB) and Java 25 or
newer. Android Studio's bundled Java is found automatically; otherwise set `JAVA25=/path/to/java`. To
get the `.jar` again:

```bash
curl -o tools/firestore-emulator/cloud-firestore-emulator-v1.23.0.jar https://storage.googleapis.com/firebase-preview-drop/emulator/cloud-firestore-emulator-v1.23.0.jar
```

After changing `firestore.rules`, run the tests, then paste the rules into the Firebase console and
publish them.

### Isometric exercises

Any exercise can be set to **Isometric**: in a plan, use the Type chips; during a workout, use the setup
line under the exercise name. Isometric sets are still logged as weight × reps. The exercise shows an
"Isometric" label, and its weights are remembered separately from the normal version, so an isometric
leg press never pre-fills from a regular leg press.

### Gyms and machine brands

- **Setting up gyms:** use **⋮ → Gyms** on the home screen. "Our gym" (Technogym and gym80) is created
  automatically. You can add other gyms with their machine brands and choose which one is home.
- **Starting a workout:** when you have more than one gym, the app asks where you're training. The gym
  chip at the top of a workout changes it afterwards.
- **Per exercise:** tap the small **Machine / gym** line under an exercise name to record the brand
  used, such as gym80, or to mark that one exercise as done at a different gym. Plans can store a
  preferred brand for machine and cable exercises.
- **Last weights** are remembered per *exercise + gym + brand*, so a gym80 leg press never pre-fills
  from a Technogym one or from another gym. Changing gym or brand mid-workout swaps the pre-filled
  weights on sets you haven't ticked off or typed over.

## Firebase setup (one time)

1. Go to <https://console.firebase.google.com> and choose **Add project**. Name it anything, e.g. `gym`.
   Analytics is optional.
2. Under **Build → Authentication → Get started → Sign-in method**, enable **Google** and pick a
   support email.
3. Under **Build → Firestore Database → Create database**, pick a region near you and start in
   **production mode**.
4. Under **Firestore → Rules**, paste the contents of [`firestore.rules`](firestore.rules) and click **Publish**.
5. Under **Project settings → Your apps → Add app → Android**, enter the package name `com.gym.app`
   and your **debug SHA-1** (command below). Google sign-in fails with no clear error without it.
6. Download `google-services.json` and put it at `androidApp/google-services.json`. Do this *after*
   enabling Google sign-in, so the file includes the OAuth client the app needs.
7. Rebuild. Without that file the app builds but shows a "Firebase isn't set up" screen.

To get the debug SHA-1, which is unique to this computer:

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

When you publish to the Play Store, also add the SHA-1 from Play Console → App integrity.

For testing on the emulator, use a system image with Google Play and add a Google account under
Settings → Passwords & accounts.

## Build & run

Open the `Gym` folder in Android Studio and run `androidApp`, or from a terminal:

```bash
./gradlew :androidApp:installDebug
```

To test live sync on one machine, run two emulators, or one emulator and your phone.
Pair them, start a workout, and type on one screen.

## Exercise catalogue

The built-in exercises are in `Catalog.kt`, which has 112 exercises, including 21 kettlebell moves.
Photos and how-to steps come from [free-exercise-db](https://github.com/yuhonas/free-exercise-db),
which is public domain. Photos load from the jsDelivr CDN, pinned to one dataset commit, and Coil caches
them on the phone. The how-to text is built into the app by `tools/make_instructions.py`. Re-run that
script after adding exercises with a new image id:

```bash
python3 tools/make_instructions.py path/to/exercises.json composeApp/src/commonMain/kotlin/com/gym/app/Catalog.kt composeApp/src/commonMain/kotlin/com/gym/app/ExerciseInstructions.kt
```

Exercises you create in the app are stored per pair in Firestore and have no photo.

## Data layout

See the comment at the top of `composeApp/src/commonMain/kotlin/com/gym/app/Models.kt`.
Each set is its own document, so both of you can edit at the same time without overwriting each other.

## Later

- iPhone: add `iosArm64()` and `iosSimulatorArm64()` targets, an Xcode wrapper project, and the Firebase
  iOS SDK. Also add an iOS `GoogleAuth` implementation using the GoogleSignIn SDK.
- The final app name, which also means picking a real `applicationId` before publishing. Changing it
  needs a new Android app entry in Firebase.
