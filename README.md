# PersonaLore

A private, offline journal for Android: your life, in your own words.

Everything stays on your phone. There are no accounts, no cloud sync and no internet permission.

## Features

- **Entries** with text, photos, voice recordings, tags and a mood rating, on any date
- **Prompt generator** with 228 built-in prompts across 21 categories, including childhood, past relationships, beliefs, spirituality, grounding, anxiety, attachment and more
  - Pick a category, or get a random surprise prompt
  - A daily prompt on the home screen
  - "Go deeper" follow-up questions you can add to an entry
  - Write your own prompts and categories
  - Six multi-day guided series (e.g. *Your Childhood in 7 Days*, *A Spiritual Journey*)
  - Favorite prompts, skip ones you don't want, and track which you've answered
- **Browse** by calendar, search with tag and mood filters, and "On this day" memories from past years
- **Insights**: writing streaks, an activity grid, mood trends over 30 days, 90 days or a year, and top tags
- **Daily reminder** that shows today's prompt (skipped if you've already written)
- **Encrypted on the phone**: entries, photos and voice notes are AES-256-GCM encrypted at rest, with the key unlocked by your PIN, fingerprint or a recovery key
- **PIN and fingerprint lock** with a one-time recovery key, lockout after repeated wrong PINs, screenshot blocking and an immediate re-lock when you leave the app
- **Data safety**: 30-day trash with undo, atomic saves, 7 rolling daily snapshots, and undoable restores
- **Backup** to a single .zip file (entries, photos, voice notes and prompt progress); restore merges by default or replaces everything
- **Markdown export** so your writing outlives the app
- **Photo privacy**: location and other EXIF data are stripped on import
- **Searchable voice notes**: give each one a label
- **Look**: OLED black with amber by default, plus Ember, Rose, Sage, Ocean, Violet and Silver colorways in dark or light mode

## Building

Requires JDK 17+ and the Android SDK (platform 36).

```
./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # release APK (needs keystore.properties, see below)
./gradlew testDebugUnitTest    # renders screenshots of every screen to app/build/screens
```

### Release signing

Create `keystore.properties` in the project root (it's git-ignored):

```
storeFile=personalore-release.jks
storePassword=...
keyAlias=personalore
keyPassword=...
```

Always sign updates with the same key, or Android won't install them over the existing app. Never commit the keystore to GitHub.

## Data

Stored encrypted in the app's private storage: `entries.json`, `prompts.json`, a `media/` folder and `snapshots/`. Android cloud backup is disabled and the app has no internet permission, so nothing leaves the device unless you export a backup yourself. Backup files are **not** encrypted (so they can be restored on a new phone), so keep them somewhere private.
