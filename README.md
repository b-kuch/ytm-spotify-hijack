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

1. If the YouTube / YouTube Music apps are installed: App info → **Open by default** → turn off *Open supported links*.
2. Open this app → **Open link settings** → *Add link* → tick all the YouTube domains. The app shows which domains are enabled.

Or skip that and just use **Share → Open in Spotify**.

### Using it from Chrome (no YouTube apps)

Once the domains are enabled, Chrome hands YouTube links to this app when you tap them on another site, in Google results, or in other apps (chat, mail…). Chrome does **not** hand off clicks made inside youtube.com / music.youtube.com (the site navigates in-page) or URLs typed into the address bar — use Chrome's **Share → Open in Spotify** there.

**Open in YouTube** opens the link in your default browser (Chrome if none is set) using a non-redirecting URL (`m.youtube.com` / `music.youtube.com`), so Chrome doesn't bounce it back here.

### Optional: Spotify API credentials

Create an app at <https://developer.spotify.com/dashboard> (any redirect URI), paste the Client ID and Secret into the app. Used only for the client-credentials search fallback.

## Building

CI (`.github/workflows/build.yml`) runs unit tests and builds a debug APK on every push — download it from the workflow run's artifacts.

Locally: `./gradlew assembleDebug` (needs the Android SDK, compileSdk 35). Output: `app/build/outputs/apk/debug/app-debug.apk`.

### Signing (so new builds install as updates)

Android only installs an APK over an existing one if both are signed with the same key. CI signs with a key from repository secrets; without them each run uses a throwaway key and you must uninstall before installing a newer build. One-time setup on any machine with Java:

```sh
keytool -genkeypair -keystore signing.keystore -storetype PKCS12 -alias ytmhijack \
  -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=ytm-spotify-hijack"
base64 -w0 signing.keystore   # macOS: base64 -i signing.keystore
```

Then in GitHub → Settings → Secrets and variables → Actions add:

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | the base64 output |
| `SIGNING_STORE_PASSWORD` | the keystore password you chose |
| `SIGNING_KEY_ALIAS` | `ytmhijack` |
| `SIGNING_KEY_PASSWORD` | same as the store password (PKCS12) |

Keep `signing.keystore` somewhere safe and never commit it. The version code is the CI run number, so every build is newer than the last.
