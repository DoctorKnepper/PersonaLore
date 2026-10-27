# PersonaLore

A private, offline journal for Android: your life, in your own words.

Everything stays on your phone. There are no accounts, no cloud sync and no internet permission.

## Features

- **Entries** with text, photos, voice recordings, tags and a mood rating, on any date
- **Prompt generator** with 184 built-in prompts across 16 categories, including childhood, family, past relationships, beliefs, spirituality and more
  - Pick a category, or get a random surprise prompt
  - A daily prompt on the home screen
  - "Go deeper" follow-up questions you can add to an entry
  - Write your own prompts and categories
  - Six multi-day guided series (e.g. *Your Childhood in 7 Days*, *A Spiritual Journey*)
  - Favorite prompts, skip ones you don't want, and track which you've answered
- **Browse** by calendar, search with tag and mood filters, and "On this day" memories from past years
- **Insights**: writing streaks, an activity grid, mood trends over 30 days, 90 days or a year, and top tags
- **Daily reminder** that shows today's prompt (skipped if you've already written)
- **PIN and fingerprint lock**
- **Backup** to a single .zip file (entries, media and prompt progress) and restore from it
- **Look**: OLED black with amber by default, plus Ember, Rose, Sage, Ocean, Violet and Silver colorways in dark or light mode

## Building

Requires JDK 17+ and the Android SDK (platform 35).

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

Stored in the app's private storage: `entries.json`, `prompts.json` and a `media/` folder. Android cloud backup is disabled so nothing leaves the device unless you export a backup yourself.
