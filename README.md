# ObserveMe

An Android app that uses the device camera to identify objects in real time. MLKit Image Labeling runs on every live frame; when an interesting object (food, medicine, plants, animals, etc.) is detected at high confidence, a single frame is captured and sent to Gemini for a detailed description.

## How it works

1. **Live scanning** — MLKit labels every camera frame and shows the top results as chips in the top-right corner
2. **Trigger** — when a label from the interesting-objects list is detected at ≥70% confidence, one frame is captured
3. **Deep analysis** — the captured frame is sent to Gemini 2.5 Flash, which identifies the object and provides context (nutritional info for food, common use for medicine, etc.)
4. **Result** — the description is shown in a card at the bottom of the screen for 8 seconds, then scanning resumes

## Setup

### 1. Clone the repository

```bash
git clone <repo-url>
cd observe-me
```

### 2. Get a Gemini API key

1. Go to [https://aistudio.google.com/apikey](https://aistudio.google.com/apikey)
2. Sign in with your Google account
3. Click **Create API key**
4. Copy the generated key

### 3. Add the API key to `local.properties`

Open (or create) `local.properties` in the project root and add:

```properties
GEMINI_API_KEY=your_api_key_here
```

> **Important:** `local.properties` is listed in `.gitignore` and must never be committed to version control. Keep your API key private.

### 4. Build and run

Open the project in Android Studio and run on a physical device (API 24+). Camera and MLKit require a real device — the emulator does not have camera hardware support for this use case.

## Free tier limits

The app uses **Gemini 2.5 Flash**. Free tier limits per day:

| Metric | Limit |
|---|---|
| Requests per minute | 5 |
| Requests per day | 20 |
| Tokens per minute | 250,000 |

A 60-second cooldown between Gemini calls is enforced in the app to stay within these limits during development.

## Tech stack

- **Camera2 API** — raw camera access and frame capture
- **MLKit Image Labeling** — on-device object detection, runs on every frame
- **Gemini 2.5 Flash** — cloud vision model for detailed object analysis
- **Kotlin Coroutines / Flow** — reactive pipeline from camera frames to UI
- **Jetpack Compose** — UI
- **Hilt** — dependency injection

## Permissions

- `CAMERA` — required for live scanning
