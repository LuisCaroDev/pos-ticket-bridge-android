import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val releaseRequested = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true) || it.contains("packageDistribution", ignoreCase = true)
}
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use(::load)
    } else if (releaseRequested) {
        throw GradleException(
            "Falta keystore.properties. Ejecuta scripts/generate-release-keystore.ps1 antes de compilar release.",
        )
    }
}

fun requiredSigningProperty(name: String): String =
    keystoreProperties.getProperty(name)?.takeIf(String::isNotBlank)
        ?: throw GradleException("Falta '$name' en keystore.properties.")

room {
    schemaDirectory("$projectDir/schemas")
}

// Only public HTTPS help settings are embedded; never package the entire environment.
val httpsEnvFile = rootProject.file(".env")
val httpsFileValues = if (httpsEnvFile.isFile) httpsEnvFile.readLines().mapNotNull { line ->
    val entry = line.trim().removePrefix("export ").split('=', limit = 2)
    if (entry.size != 2 || !entry[0].trim().startsWith("POS_BRIDGE_HTTPS_")) null
    else entry[0].trim() to entry[1].trim().removeSurrounding("\"").removeSurrounding("'")
}.toMap() else emptyMap()
val httpsSettingNames = listOf("POS_BRIDGE_HTTPS_SETUP_TTL_MS") +
    listOf("VIDEO", "GUIDE").flatMap { kind ->
        listOf("IOS", "ANDROID", "WINDOWS", "MACOS").map { "POS_BRIDGE_HTTPS_${kind}_$it" }
    }
fun javaString(value: String): String = "\"" + value.flatMap { char ->
    when (char) {
        '\\' -> "\\\\"; '"' -> "\\\""; '\n' -> "\\n"; '\r' -> "\\r"; '\t' -> "\\t"
        else -> char.toString()
    }.toList()
}.joinToString("") + "\""

android {
    namespace = "com.luiscarodev.posticketbridge"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.luiscarodev.posticketbridge"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        httpsSettingNames.forEach { name ->
            val value = providers.environmentVariable(name).orNull
                ?: providers.gradleProperty(name).orNull ?: httpsFileValues[name].orEmpty()
            buildConfigField("String", name, javaString(value))
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(requiredSigningProperty("storeFile"))
                storePassword = requiredSigningProperty("storePassword")
                keyAlias = requiredSigningProperty("keyAlias")
                keyPassword = requiredSigningProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "src/main/keepRules/rules.keep",
            )
        }
        create("releaseCheck") {
            initWith(getByName("release"))
            applicationIdSuffix = ".releasecheck"
            versionNameSuffix = "-releasecheck"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging.resources.excludes += setOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties",
        "META-INF/services/reactor.blockhound.integration.BlockHoundIntegration")
}

tasks.register("packageDistribution") {
    group = "distribution"
    description = "Compila, copia y genera SHA-256 de la APK release firmada."
    dependsOn("assembleRelease")

    val versionName = android.defaultConfig.versionName ?: "unknown"
    val sourceApk = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
    val destinationDir = layout.buildDirectory.dir("outputs/distribution")
    val destinationApk = destinationDir.map { it.file("pos-ticket-bridge-$versionName.apk") }
    val checksumFile = destinationDir.map { it.file("pos-ticket-bridge-$versionName.apk.sha256") }
    inputs.file(sourceApk)
    outputs.files(destinationApk, checksumFile)

    doLast {
        val source = sourceApk.get().asFile
        check(source.isFile) { "No se encontró la APK release firmada en ${source.absolutePath}." }
        val destination = destinationApk.get().asFile
        destination.parentFile.mkdirs()
        source.copyTo(destination, overwrite = true)
        val digest = MessageDigest.getInstance("SHA-256")
        destination.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val checksum = digest.digest().joinToString("") { "%02x".format(it) }
        checksumFile.get().asFile.writeText("$checksum  ${destination.name}\n")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.netty)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.zxing.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.ktor.server.test.host)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
