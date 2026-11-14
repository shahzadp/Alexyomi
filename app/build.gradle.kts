import mihon.buildlogic.Config
import mihon.buildlogic.getBuildTime
import mihon.buildlogic.getCommitCount
import mihon.buildlogic.getGitSha
import java.util.Properties

plugins {
    id("mihon.android.application")
    id("mihon.android.application.compose")
    id("com.github.zellius.shortcut-helper")
    kotlin("plugin.serialization")
}

shortcutHelper.setFilePath("./shortcuts.xml")

// Signing credentials. They live in a gitignored file next to the keystore, so they are
// never part of the repository. Without that file the release build falls back to no
// signing config at all.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystorePropertiesFile.exists() &&
    keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "eu.kanade.tachiyomi"

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "xyz.jmir.tachiyomi.mi"

        versionCode = 139
        versionName = "0.18.2.8"

        // The scheme the tracker logins redirect to. It has to match the one that is
        // registered at the tracker, so only the release build can use it - otherwise two
        // installed builds would fight over the same redirect.
        manifestPlaceholders["authScheme"] = "aniyomi"

        buildConfigField("String", "COMMIT_COUNT", "\"${getCommitCount()}\"")
        buildConfigField("String", "COMMIT_SHA", "\"${getGitSha()}\"")
        buildConfigField("String", "BUILD_TIME", "\"${getBuildTime(useLastCommitTime = false)}\"")
        buildConfigField("boolean", "UPDATER_ENABLED", "${Config.enableUpdater}")

        // Put these fields in acra.properties
        // val acraProperties = Properties()
        // rootProject.file("acra.properties")
        //     .takeIf { it.exists() }
        //     ?.let { acraProperties.load(FileInputStream(it)) }
        // val acraUri = acraProperties.getProperty("ACRA_URI", "")
        // val acraLogin = acraProperties.getProperty("ACRA_LOGIN", "")
        // val acraPassword = acraProperties.getProperty("ACRA_PASSWORD", "")
        // buildConfigField("String", "ACRA_URI", "\"$acraUri\"")
        // buildConfigField("String", "ACRA_LOGIN", "\"$acraLogin\"")
        // buildConfigField("String", "ACRA_PASSWORD", "\"$acraPassword\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        val debug by getting {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-${getCommitCount()}"
            isPseudoLocalesEnabled = true

            // The release build owns the tracker redirect, so the debug build must not
            // claim it - otherwise the sign in can end up in the wrong app.
            manifestPlaceholders["authScheme"] = "aniyomi-debug"
        }
        val release by getting {
            isMinifyEnabled = Config.enableCodeShrink
            isShrinkResources = Config.enableCodeShrink

            proguardFiles("proguard-android-optimize.txt", "proguard-rules.pro")

            buildConfigField("String", "BUILD_TIME", "\"${getBuildTime(useLastCommitTime = true)}\"")

            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        val commonMatchingFallbacks = listOf(release.name)

        create("benchmark") {
            initWith(release)

            isDebuggable = false
            isProfileable = true
            versionNameSuffix = "-benchmark"
            applicationIdSuffix = ".benchmark"

            signingConfig = debug.signingConfig

            matchingFallbacks.addAll(commonMatchingFallbacks)
        }
    }

    sourceSets {
        getByName("benchmark").res.srcDirs("src/debug/res")
    }

    splits {
        abi {
            isEnable = true
            isUniversalApk = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    packaging {
        jniLibs {
            keepDebugSymbols += listOf(
                "libandroidx.graphics.path",
                "libarchive-jni",
                "libavcodec",
                "libavdevice",
                "libavfilter",
                "libavformat",
                "libavutil",
                "libconscrypt_jni",
                "libc++_shared",
                "libffmpegkit_abidetect",
                "libffmpegkit",
                "libimagedecoder",
                "liblibrary",
                "libmpv",
                "libplayer",
                "libpostproc",
                "libquickjs",
                "libsqlite3x",
                "libswresample",
                "libswscale",
                "libxml2",
            )
                .map { "**/$it.so" }
        }
        resources {
            excludes += setOf(
                "kotlin-tooling-metadata.json",
                "LICENSE.txt",
                "META-INF/**/*.properties",
                "META-INF/**/LICENSE.txt",
                "META-INF/*.properties",
                "META-INF/*.version",
                "META-INF/**/*.MF",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/NOTICE",
                "META-INF/README.md",
            )
        }
    }

    dependenciesInfo {
        includeInApk = Config.includeDependencyInfo
        includeInBundle = Config.includeDependencyInfo
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true

        // Disable some unused things
        aidl = false
        renderScript = false
        shaders = false
    }

    androidResources {
        // Ship only the English strings. The 64 translations are kept in the repository
        // (i18n/ and i18n-aniyomi/) and can be re-enabled by extending this list.
        localeFilters += listOf("en")

        // The bundled subtitle font is 6 MB and mpv already renders subtitles with the
        // system fonts (see PlayerActivity.copyFontsDirectory), so it is not packaged.
        ignoreAssetsPattern += "subfont.ttf"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=coil3.annotation.ExperimentalCoilApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.coroutines.InternalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

dependencies {
    implementation(projects.i18n)
    implementation(projects.i18nAniyomi)
    implementation(projects.core.archive)
    implementation(projects.core.common)
    implementation(projects.coreMetadata)
    implementation(projects.sourceApi)
    implementation(projects.sourceLocal)
    implementation(projects.data)
    implementation(projects.domain)
    implementation(projects.presentationCore)

    // Compose
    implementation(compose.activity)
    implementation(compose.foundation)
    implementation(compose.material3.core)
    implementation(compose.material.icons)
    implementation(compose.animation)
    implementation(compose.animation.graphics)
    debugImplementation(compose.ui.tooling)
    implementation(compose.ui.tooling.preview)
    implementation(compose.ui.util)

    implementation(androidx.interpolator)

    implementation(androidx.paging.runtime)
    implementation(androidx.paging.compose)

    implementation(libs.bundles.sqlite)

    implementation(kotlinx.reflect)
    implementation(kotlinx.immutables)

    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.bundles.coroutines)

    // AndroidX libraries
    implementation(androidx.annotation)
    implementation(androidx.appcompat)
    implementation(androidx.biometricktx)
    implementation(androidx.constraintlayout)
    implementation(aniyomilibs.compose.constraintlayout)
    implementation(androidx.corektx)
    implementation(androidx.localbroadcastmanager)
    implementation(androidx.splashscreen)
    implementation(androidx.recyclerview)
    implementation(androidx.viewpager)
    implementation(androidx.profileinstaller)
    implementation(aniyomilibs.mediasession)

    implementation(androidx.bundles.lifecycle)

    // Job scheduling
    implementation(androidx.workmanager)

    // RxJava
    implementation(libs.rxjava)

    // Networking
    implementation(libs.bundles.okhttp)
    implementation(libs.okio)
    implementation(libs.conscrypt.android) // TLS 1.3 support for Android < 10

    // Data serialization (JSON, protobuf, xml)
    implementation(kotlinx.bundles.serialization)

    // HTML parser
    implementation(libs.jsoup)

    // Disk
    implementation(libs.disklrucache)
    implementation(libs.unifile)

    // Preferences
    implementation(libs.preferencektx)

    // Dependency injection
    implementation(libs.injekt)

    // Image loading
    implementation(platform(libs.coil.bom))
    implementation(libs.bundles.coil)
    implementation(libs.subsamplingscaleimageview) {
        exclude(module = "image-decoder")
    }
    implementation(libs.image.decoder)

    // UI libraries
    implementation(libs.material)
    implementation(libs.photoview)
    implementation(libs.directionalviewpager) {
        exclude(group = "androidx.viewpager", module = "viewpager")
    }
    implementation(libs.insetter)
    implementation(libs.bundles.richtext)
    implementation(libs.bundles.voyager)
    implementation(libs.compose.materialmotion)
    implementation(libs.swipe)
    implementation(libs.compose.webview)
    implementation(libs.compose.grid)
    implementation(libs.reorderable)

    // Logging
    implementation(libs.logcat)

    // Shizuku
    implementation(libs.bundles.shizuku)

    // Tests
    testImplementation(libs.bundles.test)

    // For detecting memory leaks; see https://square.github.io/leakcanary/
    // debugImplementation(libs.leakcanary.android)

    implementation(libs.leakcanary.plumber)

    testImplementation(kotlinx.coroutines.test)

    // mpv-android
    implementation(aniyomilibs.aniyomi.mpv)
    // FFmpeg-kit
    implementation(aniyomilibs.ffmpeg.kit)
    implementation(aniyomilibs.arthenica.smartexceptions)
    // seeker seek bar
    implementation(aniyomilibs.seeker)
    // true type parser
    implementation(aniyomilibs.truetypeparser)
}

androidComponents {
    beforeVariants { variantBuilder ->
        // Disables standardBenchmark
        if (variantBuilder.buildType == "benchmark") {
            variantBuilder.enable = variantBuilder.productFlavors.containsAll(
                listOf("default" to "dev"),
            )
        }
    }
    onVariants { variant ->
        // The mpv library bundles a 6 MB subtitle font that we don't use (mpv renders subtitles
        // with the system fonts directory), so it is removed before the assets are compressed.
        val variantName = variant.name
        val capitalized = variantName.replaceFirstChar { it.uppercase() }
        val mergedAssets = layout.buildDirectory.dir(
            "intermediates/assets/$variantName/merge${capitalized}Assets",
        )
        val compressedAssets = layout.buildDirectory.dir(
            "intermediates/compressed_assets/$variantName/compress${capitalized}Assets",
        )

        val stripSubtitleFont = tasks.register("stripSubtitleFont$capitalized") {
            val assetsDir = mergedAssets
            val compressedDir = compressedAssets
            dependsOn("merge${capitalized}Assets")
            inputs.dir(assetsDir)
            doLast {
                val font = assetsDir.get().asFile.resolve("subfont.ttf")
                if (font.exists() && font.length() > 0) {
                    // Truncate instead of deleting: the asset merger keeps a file list that the
                    // compression step validates, and mpv doesn't need the font (it renders
                    // subtitles with the system fonts directory).
                    font.writeBytes(ByteArray(0))
                    logger.lifecycle("Removed bundled subtitle font from $variantName assets")
                }
            }
        }

        // The compression step has to run after the font was removed
        tasks.matching { it.name == "compress${capitalized}Assets" }.configureEach {
            dependsOn(stripSubtitleFont)
            // The cached output would contain the removed font again
            outputs.doNotCacheIf("The bundled subtitle font is removed after the assets are merged") { true }
        }

        // Same for the packaging step, it would restore an APK with the font
        tasks.matching { it.name == "package$capitalized" }.configureEach {
            outputs.doNotCacheIf("The bundled subtitle font is removed from the packaged assets") { true }
        }
    }
    onVariants(selector().withFlavor("default" to "standard")) {
        // Only excluding in standard flavor because this breaks
        // Layout Inspector's Compose tree
        it.packaging.resources.excludes.add("META-INF/*.version")
    }
}

buildscript {
    dependencies {
        classpath(kotlinx.gradle)
    }
}
