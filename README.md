# FitLife - Fitness Tracking Android App

A modern fitness tracking application built with Kotlin for Android. Features workout routine management, equipment checklists with SMS delegation, and gym location geotagging.

## Features

- 🔐 **Firebase Authentication** - Secure email/password authentication
- 🏋️ **Workout Routines** - Create and manage weekly workout plans
- ✅ **Equipment Checklist** - Track equipment with SMS delegation
- 📍 **Geotagging** - Save and navigate to your favorite workout locations
- 🌙 **Dark Mode** - Beautiful Warm Ink dark theme

## Setup Instructions

### 1. Clone the Repository

```bash
git clone <your-repo-url>
cd FitLife
```

### 2. Configure Google Maps API

1. Go to [Google Cloud Console](https://console.cloud.google.com/google/maps-apis)
2. Create a new project or select an existing one
3. Enable "Maps SDK for Android"
4. Create an API key
5. Copy `local.properties.example` to `local.properties`
6. Add your API key:
   ```properties
   MAPS_API_KEY=your_actual_api_key_here
   ```

### 3. Configure Firebase

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Create a new project or select an existing one
3. Add an Android app with package name: `com.example.fitlife`
4. Download `google-services.json`
5. Place it in the `app/` directory

#### Firebase Services Used:
- **Firebase Authentication** - Enable Email/Password sign-in method in Firebase Console

### 4. Build and Run

```bash
./gradlew assembleDebug
```

Or open in Android Studio and run directly.

## Project Structure

```
app/
├── src/main/
│   ├── java/com/example/fitlife/
│   │   ├── data/
│   │   │   ├── dao/          # Room database DAOs
│   │   │   ├── model/        # Data models
│   │   │   └── repository/   # Repository pattern
│   │   ├── ui/
│   │   │   ├── auth/         # Login & Registration
│   │   │   ├── checklist/    # Equipment checklist
│   │   │   ├── home/         # Dashboard
│   │   │   ├── map/          # Geotagging
│   │   │   ├── profile/      # User profile
│   │   │   └── routines/     # Workout routines
│   │   └── utils/            # Utility classes
│   └── res/
│       ├── layout/           # XML layouts
│       ├── navigation/       # Navigation graph
│       └── values/           # Colors, strings, themes
```

## Tech Stack

- **Language**: Kotlin
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Architecture**: Repository Pattern
- **Database**: Room (local storage)
- **Authentication**: Firebase Auth
- **Maps**: Google Maps SDK
- **UI**: Material Design 3

## Color Themes

### Light Mode (Cloud Dancer)
- Background: PANTONE 11-4201 TCX Cloud Dancer (`#F0EDE5`)

### Dark Mode (Warm Ink)
- Background: Warm Ink (`#2A2825`) - A warmer, darker variant

## Security Notes

⚠️ **Never commit the following files:**
- `local.properties` - Contains SDK path and API keys
- `app/google-services.json` - Firebase configuration
- `.env` or `.env.local` - Environment variables
- Any `*.keystore` or `*.jks` files

These files are already in `.gitignore`.

## License

This project is for educational purposes.

```
idleshade@Quanta

User: @prabin
```
