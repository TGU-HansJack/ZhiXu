import java.io.File
import java.net.URL
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile)
}

configurations.configureEach {
    exclude(group = "org.jetbrains", module = "annotations-java5")
}

val ocrDepsDir = layout.buildDirectory.dir("ocrNativeDeps")
val ocrDownloadsDir = layout.buildDirectory.dir("ocrNativeDeps/downloads")
val ocrExtractDir = layout.buildDirectory.dir("ocrNativeDeps/extracted")

val ncnnAndroidSharedUrls =
    listOf(
        "https://github.com/Tencent/ncnn/releases/download/20250916/ncnn-20250916-android-shared.zip",
        "https://ghproxy.com/https://github.com/Tencent/ncnn/releases/download/20250916/ncnn-20250916-android-shared.zip",
    )
val opencvAndroidUrls =
    listOf(
        // Official OpenCV Android SDK (shared libs) to avoid OpenMP/static-link issues.
        "https://github.com/opencv/opencv/releases/download/4.10.0/opencv-4.10.0-android-sdk.zip",
        "https://ghproxy.com/https://github.com/opencv/opencv/releases/download/4.10.0/opencv-4.10.0-android-sdk.zip",
    )

fun sha256(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

fun downloadIfMissing(dest: File, urls: List<String>) {
    if (dest.exists() && dest.length() > 0L) return
    dest.parentFile?.mkdirs()
    // Prefer curl.exe for reliability on Windows CI networks.
    for (u in urls) {
        val curlOk =
            runCatching {
                exec {
                    commandLine(
                        "curl.exe",
                        "-L",
                        "--retry",
                        "8",
                        "--retry-delay",
                        "2",
                        "--max-time",
                        "600",
                        u,
                        "-o",
                        dest.absolutePath,
                    )
                    isIgnoreExitValue = false
                }
                dest.exists() && dest.length() > 0L
            }.getOrDefault(false)
        if (curlOk) return
        runCatching { dest.delete() }
    }

    // Fallback: Java URL connection.
    val attempts = 4
    var lastErr: Throwable? = null
    for (i in 1..attempts) {
        try {
            for (u in urls) {
                val conn = URL(u).openConnection().apply {
                    connectTimeout = 30_000
                    readTimeout = 180_000
                }
                conn.getInputStream().use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (dest.length() > 0L) return
                runCatching { dest.delete() }
            }
        } catch (t: Throwable) {
            lastErr = t
            runCatching { dest.delete() }
            if (i < attempts) Thread.sleep(1_500L * i)
        }
    }
    throw lastErr ?: error("download failed: ${urls.firstOrNull().orEmpty()}")
}

val prepareOcrNativeDeps =
    tasks.register("prepareOcrNativeDeps") {
        outputs.dir(ocrDepsDir)
        doLast {
            val depsDir = ocrDepsDir.get().asFile
            val downloadsDir = ocrDownloadsDir.get().asFile
            val extractedDir = ocrExtractDir.get().asFile
            val includeDir = File(depsDir, "include")
            val jniLibsRoot = File(depsDir, "jniLibs")

            downloadsDir.mkdirs()
            extractedDir.mkdirs()
            includeDir.mkdirs()
            jniLibsRoot.mkdirs()

            val ncnnZip = File(downloadsDir, "ncnn-android-shared.zip")
            val opencvZip = File(downloadsDir, "opencv-android-sdk.zip")

            downloadIfMissing(ncnnZip, ncnnAndroidSharedUrls)
            downloadIfMissing(opencvZip, opencvAndroidUrls)

            val ncnnOut = File(extractedDir, "ncnn")
            val opencvOut = File(extractedDir, "opencv-sdk")
            if (!ncnnOut.exists()) copy { from(zipTree(ncnnZip)); into(ncnnOut) }
            if (!opencvOut.exists()) copy { from(zipTree(opencvZip)); into(opencvOut) }

            // Normalize include dirs.
            // - ncnn: include/ncnn/*.h
            // - opencv: include/opencv2/... (from OpenCV Android SDK)
            val ncnnInclude =
                ncnnOut.walkTopDown().firstOrNull { it.isDirectory && it.name == "include" && File(it, "ncnn").isDirectory }
                    ?: error("ncnn include/ not found")
            val opencvInclude =
                opencvOut.walkTopDown().firstOrNull { it.isDirectory && File(it, "opencv2").isDirectory }
                    ?: opencvOut.walkTopDown().firstOrNull { it.isDirectory && it.name == "include" && File(it, "opencv2").isDirectory }
                    ?: error("opencv include/ not found")

            copy { from(ncnnInclude); into(includeDir) }
            copy { from(opencvInclude); into(includeDir) }

            // Normalize jniLibs: only keep arm64-v8a (the app is arm64-only).
            val abi = "arm64-v8a"
            val abiDir = File(jniLibsRoot, abi).apply { mkdirs() }

            val ncnnSo =
                ncnnOut.walkTopDown().firstOrNull { it.isFile && it.name == "libncnn.so" && it.path.contains("${File.separator}$abi${File.separator}") }
                    ?: error("libncnn.so for $abi not found")
            copy { from(ncnnSo); into(abiDir) }

            // OpenCV Android SDK uses libopencv_java4.so (monolithic shared library).
            val opencvSo =
                opencvOut.walkTopDown().firstOrNull {
                    it.isFile && it.name == "libopencv_java4.so" && it.path.contains("${File.separator}$abi${File.separator}")
                } ?: error("libopencv_java4.so for $abi not found")
            copy { from(opencvSo); into(abiDir) }

            // Store a small fingerprint for debugging.
            val meta =
                File(depsDir, "deps.json").apply {
                    writeText(
                        """
                        {
                          "ncnnUrl": "${ncnnAndroidSharedUrls.first()}",
                          "opencvUrl": "${opencvAndroidUrls.first()}",
                          "ncnnZipSha256": "${sha256(ncnnZip)}",
                          "opencvZipSha256": "${sha256(opencvZip)}"
                        }
                        """.trimIndent() + "\n",
                    )
                }
            meta.length()
        }
    }

android {
    namespace = "app.zhixu"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.zhixu"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 3
        versionName = "0.2.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        // J2V8 AAR currently bundles both arm64-v8a and armeabi-v7a native libs.
        // Restrict to arm64-v8a to reduce APK size and avoid packaging unused ABIs.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf("-DOCR_DEPS_DIR=${ocrDepsDir.get().asFile.absolutePath}")
                cppFlags += listOf("-frtti", "-fexceptions")
            }
        }
    }

    packaging {
        // Some J2V8 artifacts include desktop native binaries in the jar resources (e.g. macOS dylib).
        // Exclude them from the APK to avoid massive size bloat.
        resources {
            excludes += setOf("**/*.dylib")
        }
        // Ensure native libs are extractable/deflate-compressed; helps compatibility and reduces APK size.
        jniLibs {
            useLegacyPackaging = true
        }
    }

    sourceSets {
        getByName("main") {
            // Native dependencies fetched at build time (ncnn + opencv).
            jniLibs.srcDir(ocrDepsDir.map { it.dir("jniLibs") })
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            isDebuggable = false
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
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
    }
}

tasks.named("preBuild").configure { dependsOn(prepareOcrNativeDeps) }
tasks.matching { it.name.startsWith("externalNativeBuild") }.configureEach { dependsOn(prepareOcrNativeDeps) }

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.google.material)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.markwon.core)
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.github.CanHub:Android-Image-Cropper:4.5.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
    implementation("com.google.guava:listenablefuture:1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")
    implementation("androidx.webkit:webkit:1.11.0")
    // Native V8 for Android (J2V8).
    implementation("com.eclipsesource.j2v8:j2v8_android_arm64-v8a:6.3.0@aar")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-tasklist:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-latex:4.6.2")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation(libs.leakcanary.android)
    debugImplementation(libs.androidx.metrics.performance)

    releaseImplementation(libs.androidx.profileinstaller)

    baselineProfile(project(":apps:zhixu-android-benchmark"))
}
