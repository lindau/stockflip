# Technology Stack

**Analysis Date:** 2026-03-01

## Languages

**Primary:**
- Kotlin 1.9.24 - Android application code and tests
- Java 17 - Compilation target

**Secondary:**
- XML - Android manifest, resources, and layouts

## Runtime

**Environment:**
- Android SDK (API level 24 minimum, 35 target)
- Android Runtime (Kotlin compiled to JVM bytecode)

**Package Manager:**
- Gradle 8.12.3 - Build tool and dependency management
- Lockfile: `gradle-wrapper.jar` (present)

## Frameworks

**Core:**
- Android Framework (API 24-35) - Base Android platform
- Android Jetpack - Modern Android development components

**UI:**
- Jetpack Compose 1.5.4 - Modern declarative UI framework
- Material Design 3 - UI component library (material3:1.12.0)
- Material Icons Extended (compose-material-icons-extended)
- Android ConstraintLayout 2.2.0 - Legacy layout support
- SwipeRefreshLayout 1.1.0 - Pull-to-refresh UI component

**Lifecycle & State Management:**
- Androidx Lifecycle 2.8.7 - runtime-ktx, viewmodel-ktx, livedata-ktx
- Androidx Fragment 1.8.5 - Fragment management

**Database:**
- Room 2.6.1 - ORM and SQLite abstraction (runtime, ktx, compiler)
- SQLite 3 - Local database storage (via Room)

**Networking:**
- Retrofit 2.11.0 - HTTP client framework with declarative APIs
- OkHttp 4.12.0 - Underlying HTTP client, logging interceptor, cookie management
- Gson 2.11.0 - JSON serialization/deserialization

**Background Work:**
- Androidx WorkManager 2.9.1 - Background task scheduling

**Coroutines:**
- Kotlinx Coroutines 1.7.3 - core and android variants for async/concurrent operations

## Key Dependencies

**Critical:**
- Room 2.6.1 - Handles all local data persistence and stock/watchlist database operations
- Retrofit 2.11.0 + OkHttp 4.12.0 - Powers HTTP requests to external stock data APIs
- Jetpack Compose 1.5.4 - Modern UI rendering framework for Android UI
- Kotlinx Coroutines 1.7.3 - Enables async API calls and background operations
- WorkManager 2.9.1 - Manages periodic price update background jobs

**Infrastructure:**
- Androidx Core KTX 1.15.0 - Kotlin extensions for Android framework
- Androidx AppCompat 1.7.0 - Backward compatibility support
- Androidx Activity-Compose 1.8.2 - Compose integration with Activity lifecycle

## Configuration

**Environment:**
- API Key Configuration: Finnhub API key read from `local.properties` file (FINNHUB_API_KEY)
- BuildConfig: Finnhub API key injected at compile-time from local.properties
- Environment File: `local.properties` (not committed to repository)
- Configuration Method: Build-time property injection into BuildConfig class

**Build:**
- `build.gradle` - Root project build configuration
- `app/build.gradle` - Application module build configuration with dependencies
- `settings.gradle` - Multi-module project configuration
- `gradle.properties` - Gradle system properties (JVM args, AndroidX configuration)
- `local.properties` - Local development environment (API keys, SDK paths)

**Room Database:**
- Schema Export: Enabled in `build.gradle`
- Schema Location: `app/schemas/com.stockflip.StockPairDatabase/`
- Current Version: 8 (with 4 migration paths: 4→5, 5→6, 6→7, 7→8)

## Platform Requirements

**Development:**
- Android Studio or compatible IDE
- Android SDK API level 24 minimum (for compilation)
- Java JDK 17 or higher
- Gradle 8.12.3 or higher

**Runtime/Production:**
- Android device or emulator with API level 24 or higher
- Target SDK 35 (Android 15 compatibility)
- Minimum 5MB application size (estimated)
- Internet connection (required for API calls to Yahoo Finance and Finnhub)
- Android WorkManager background service execution

---

*Stack analysis: 2026-03-01*
