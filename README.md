# YT → Spotify

Android app that catches YouTube / YouTube Music links and opens the same **song or album** in the Spotify app — with an escape hatch back to YouTube.

## How it works

1. A link arrives (tapped link, or **Share → "Open in Spotify"** from the YouTube / YT Music app).
2. A small dialog appears: *"Opening in Spotify in N s"* with an **Open in YouTube** button (the escape hatch) and **Spotify now**. Back / tap outside cancels.
3. The link is resolved:
   - **[Odesli / song.link](https://odesli.co)** maps the YouTube id straight to a Spotify track/album (no key needed).
   - Otherwise the title is read via YouTube oEmbed, cleaned (`(Official Video)`, `- Topic`, `VEVO` …) and searched on Spotify:
     - with optional Spotify API credentials → the best match opens directly;
     - without → Spotify opens its search screen with the query.
   - Plain `youtube.com` videos that don't look like music (not on Odesli, no `- Topic`/`VEVO`/"official video" hints) go to YouTube automatically.

Supported links: `music.youtube.com/watch`, `/playlist?list=OLAK5uy_…` (albums), `/browse/MPREb_…`, `youtube.com/watch`, `/playlist`, `/shorts/…`, `youtu.be/…`.

## Escape hatches

- **Open in YouTube** button in the dialog (opens YT Music / YouTube explicitly, so it doesn't loop back).
- Settings: master switch **Redirect YouTube links to Spotify** — off means every link goes straight to YouTube (sharing to "Open in Spotify" still works).
- Settings: **Also handle regular youtube.com links** — turn off to only hijack `music.youtube.com`.
- Adjustable escape-hatch window (0–5 s).

## Setup on the phone

Android 12+ doesn't let unverified apps grab web links automatically, and the YouTube apps claim them first:

1. YouTube and YouTube Music → App info → **Open by default** → turn off *Open supported links*.
2. Open this app → **Open link settings** → *Add link* → tick all the YouTube domains. The app shows which domains are enabled.

Or skip that and just use **Share → Open in Spotify**.

### Optional: Spotify API credentials

Create an app at <https://developer.spotify.com/dashboard> (any redirect URI), paste the Client ID and Secret into the app. Used only for the client-credentials search fallback.

## Building

CI (`.github/workflows/build.yml`) runs unit tests and builds a debug APK on every push — download it from the workflow run's artifacts.

Locally: `./gradlew assembleDebug` (needs the Android SDK, compileSdk 35). Output: `app/build/outputs/apk/debug/app-debug.apk`.

Note: CI debug builds are signed with a fresh debug key each run, so installing a newer build may require uninstalling the old one first.
