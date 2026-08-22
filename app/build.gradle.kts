import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Credenciales de firma para el release. Se leen de `keystore.properties`
// (local, gitignored) o de variables de entorno (GitHub Actions). Si no hay
// ninguna, el release se genera sin firmar para no romper el build.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val useEnvSigning = System.getenv("RELEASE_KEYSTORE_PASSWORD") != null
val hasReleaseSigning = useEnvSigning || keystoreProps.containsKey("keystoreFile")

android {
    namespace = "com.example.musicplayer"
    // Media3 1.10.1 exige compileSdk 36 (AGP 8.9+).
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.musicplayer"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.3.2"

        // Configuración del repo de GitHub usado por el sistema de actualizaciones.
        buildConfigField("String", "REPO_OWNER", "\"LaManditacabra\"")
        buildConfigField("String", "REPO_NAME", "\"AsyncPlay-Reproductor-de-Musica-\"")
    }

    signingConfigs {
        // Keystore propio de release: la firma NO depende de la máquina,
        // así las actualizaciones se instalan sobre versiones anteriores.
        if (hasReleaseSigning) {
            create("release") {
                if (useEnvSigning) {
                    storeFile = rootProject.file("keystore/release.keystore")
                    storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "release"
                    keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
                } else {
                    storeFile = rootProject.file(keystoreProps.getProperty("keystoreFile"))
                    storePassword = keystoreProps.getProperty("storePassword")
                    keyAlias = keystoreProps.getProperty("keyAlias")
                    keyPassword = keystoreProps.getProperty("keyPassword")
                }
            }
        }
    }

    buildTypes {
        release {
            // Minify desactivado: NewPipeExtractor usa reflection/SPI y R8 podría
            // romper la extracción. Se mantiene el proguard-rules.pro para el futuro.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Si hay credenciales de firma configuradas (local o CI), se firma el
            // release; si no, queda sin firmar (no rompe el build).
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                null
            }
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
    implementation(libs.androidx.navigation.compose)

    // ---------------------------------------------------------------
    // Jetpack Compose + Material Design 3
    // ---------------------------------------------------------------
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
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
    implementation(libs.okhttp) // Cliente HTTP de NewPipeExtractor (gzip, cookies, redirects)

    // Glance (widgets con Compose)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
}