# TalkChinese

TalkChinese is an Android app for learning Chinese, built with Kotlin and Jetpack Compose. The first milestone is a premium red-and-gold registration and login screen backed by Supabase Auth.

## Configure Supabase

1. Create a Supabase project.
2. In Project Settings → API, copy the Project URL and the publishable key (or legacy anon key).
3. In GitHub repository Settings → Secrets and variables → Actions, add:
   - `SUPABASE_URL`
   - `SUPABASE_ANON_KEY`
4. In Supabase Authentication settings, configure email confirmation and password policies as desired.

Never put a `service_role` key or secret API key in the Android app.

For local builds, provide Gradle properties:
```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_ANON_KEY=YOUR_PUBLIC_KEY
```
Then run `gradle assembleDebug -PSUPABASE_URL="..." -PSUPABASE_ANON_KEY="..."`.

## Build APK

The GitHub Actions workflow runs on pushes to `main` and can also be started manually from Actions → Build TalkChinese Android APK. Download the `TalkChinese-debug-apk` artifact from a successful run.

## Current milestone

- [x] Android project scaffold
- [x] Red, ivory and gold Compose registration/login UI
- [x] Supabase Auth REST integration for email/password
- [x] Local session marker and access-token persistence
- [x] GitHub Actions debug APK workflow
- [ ] Configure real Supabase project secrets
- [ ] Verify a real registration and login against configured Supabase
- [ ] Add learning dashboard, lessons, dictionary and progress tracking
