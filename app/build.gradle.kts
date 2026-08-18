plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.musicplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.musicplayer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Configuración del repo de GitHub usado por el sistema de actualizaciones.
        buildConfigField("String", "REPO_OWNER", "\"LaManditacabra\"")
        buildConfigField("String", "REPO_NAME", "\"AsyncPlay-Reproductor-de-Musica-\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // ---------------------------------------------------------------
    // Core de Android
    // ---------------------------------------------------------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // ---------------------------------------------------------------
    // Lifecycle / ViewModel (MVVM)
    // ---------------------------------------------------------------
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ---------------------------------------------------------------
    // Jetpack Compose + Material Design 3
    // ---------------------------------------------------------------
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    // ---------------------------------------------------------------
    // Media3 / ExoPlayer: motor de reproducción de audio
    // ---------------------------------------------------------------
    implementation(libs.media3.exoplayer)   // ExoPlayer (player + renderers + extractores)
    implementation(libs.media3.session)     // MediaSession / MediaSessionService / MediaController
    implementation(libs.media3.common)      // Tipos base (MediaItem, Player, etc.)

    // ---------------------------------------------------------------
    // Room: base de datos local
    // ---------------------------------------------------------------
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ---------------------------------------------------------------
    // WorkManager: descargas y tareas en segundo plano
    // ---------------------------------------------------------------
    implementation(libs.work.runtime.ktx)

    // ---------------------------------------------------------------
    // Coil: carga de imágenes (portadas)
    // ---------------------------------------------------------------
    implementation(libs.coil.compose)

    // ---------------------------------------------------------------
    // NewPipeExtractor: extracción de audio/portadas desde YouTube
    // ---------------------------------------------------------------
    implementation(libs.newpipe.extractor)
}