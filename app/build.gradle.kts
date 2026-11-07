import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    alias(libs.plugins.openapi.generator)
}

/**
 * Base URL of the public API. Debug builds default to core-service on the developer's computer as
 * seen from the Android emulator (10.0.2.2:8081, plain HTTP allowed only there, see
 * src/debug/res/xml/network_security_config.xml); release builds default to nginx over HTTPS.
 */
fun apiBaseUrl(default: String): String = providers.gradleProperty("inspecto.apiBaseUrl").getOrElse(default)

/** Base URL of media-service: its own port in debug (10.0.2.2:8082), the same nginx host in release. */
fun mediaBaseUrl(default: String): String = providers.gradleProperty("inspecto.mediaBaseUrl").getOrElse(default)

/** Base URL of report-service: its own port in debug (10.0.2.2:8083), the same nginx host in release. */
fun reportBaseUrl(default: String): String = providers.gradleProperty("inspecto.reportBaseUrl").getOrElse(default)

android {
    namespace = "dev.inspecto.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.inspecto.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl("http://10.0.2.2:8081/api/v1/")}\"")
            buildConfigField("String", "MEDIA_BASE_URL", "\"${mediaBaseUrl("http://10.0.2.2:8082/api/v1/")}\"")
            buildConfigField("String", "REPORT_BASE_URL", "\"${reportBaseUrl("http://10.0.2.2:8083/api/v1/")}\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl("https://10.0.2.2/api/v1/")}\"")
            buildConfigField("String", "MEDIA_BASE_URL", "\"${mediaBaseUrl("https://10.0.2.2/api/v1/")}\"")
            buildConfigField("String", "REPORT_BASE_URL", "\"${reportBaseUrl("https://10.0.2.2/api/v1/")}\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        // Exported Room schemas for migration tests (Robolectric reads the assets of the debug build); they
        // describe tables only, and release builds do not contain them.
        getByName("debug").assets.directories.add(file("schemas").path)
        getByName("main").kotlin.directories.add(
            layout.buildDirectory
                .dir("generated/openapi/src/main/kotlin")
                .get()
                .asFile.path,
        )
        getByName("main").kotlin.directories.add(
            layout.buildDirectory
                .dir("generated/openapi-media/src/main/kotlin")
                .get()
                .asFile.path,
        )
        getByName("main").kotlin.directories.add(
            layout.buildDirectory
                .dir("generated/openapi-report/src/main/kotlin")
                .get()
                .asFile.path,
        )
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = false
        // Library versions are updated by Dependabot; "newer version available" would make
        // unchanged code fail whenever a release appears.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.useJUnitPlatform()
                // Robolectric reaches FileDescriptor internals of the JDK (Robolectric setup for Java 17+).
                it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

// ---------- API client generated from the tagged contract (ADR-014) ----------
openApiGenerate {
    generatorName.set("kotlin")
    library.set("jvm-retrofit2")
    // A URI, not a path: the OpenAPI parser rejects Windows paths when resolving references.
    inputSpec.set(rootProject.file("api/core.yaml").toURI().toString())
    outputDir.set(
        layout.buildDirectory
            .dir("generated/openapi")
            .get()
            .asFile.path,
    )
    packageName.set("dev.inspecto.android.api")
    // Internal service-to-service endpoints are not part of the client.
    openapiNormalizer.set(
        mapOf(
            "FILTER" to
                "tag:auth|users|properties|rooms|dictionaries|inspections|checklist|defects|comparison|suggestions",
        ),
    )
    configOptions.set(
        mapOf(
            "serializationLibrary" to "kotlinx_serialization",
            "useCoroutines" to "true",
            "dateLibrary" to "java8",
            "enumPropertyNaming" to "UPPERCASE",
            "omitGradleWrapper" to "true",
            "sourceFolder" to "src/main/kotlin",
        ),
    )
    cleanupOutput.set(true)
    ignoreFileOverride.set(rootProject.file("api/.openapi-generator-ignore").path)
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
}

// media-service client (photo upload, limits); a separate package because both contracts share common.yaml.
val openApiGenerateMedia by tasks.registering(org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class) {
    generatorName.set("kotlin")
    library.set("jvm-retrofit2")
    inputSpec.set(rootProject.file("api/media.yaml").toURI().toString())
    outputDir.set(
        layout.buildDirectory
            .dir("generated/openapi-media")
            .get()
            .asFile.path,
    )
    packageName.set("dev.inspecto.android.media.api")
    openapiNormalizer.set(mapOf("FILTER" to "tag:photos"))
    configOptions.set(openApiGenerate.configOptions)
    cleanupOutput.set(true)
    ignoreFileOverride.set(rootProject.file("api/.openapi-generator-ignore").path)
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
}

// report-service client (order, poll and download PDF reports); its own package, as for media-service.
val openApiGenerateReport by tasks.registering(org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class) {
    generatorName.set("kotlin")
    library.set("jvm-retrofit2")
    inputSpec.set(rootProject.file("api/report.yaml").toURI().toString())
    outputDir.set(
        layout.buildDirectory
            .dir("generated/openapi-report")
            .get()
            .asFile.path,
    )
    packageName.set("dev.inspecto.android.report.api")
    openapiNormalizer.set(mapOf("FILTER" to "tag:reports"))
    configOptions.set(openApiGenerate.configOptions)
    cleanupOutput.set(true)
    ignoreFileOverride.set(rootProject.file("api/.openapi-generator-ignore").path)
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
}

// The spec is passed as a URI string, so Gradle does not see its content: without these inputs a
// changed contract could reuse a client generated from the previous one (up-to-date check, build cache).
tasks.withType<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>().configureEach {
    inputs.files(rootProject.fileTree("api") { include("*.yaml") }).withPropertyName("contract")
}

tasks.named("preBuild") { dependsOn("openApiGenerate", openApiGenerateMedia, openApiGenerateReport) }

// ---------- Code quality (CODE_QUALITY.md §2) ----------
ktlint {
    filter { exclude { it.file.path.contains("${File.separator}generated${File.separator}") } }
}

// The generated client is part of the main source set (and excluded above), so ktlint runs after it.
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    dependsOn("openApiGenerate", openApiGenerateMedia, openApiGenerateReport)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

kover {
    reports {
        filters {
            excludes {
                // Generated code (CODE_QUALITY.md §5).
                packages(
                    "dev.inspecto.android.api",
                    "dev.inspecto.android.media.api",
                    "dev.inspecto.android.report.api",
                    "hilt_aggregated_deps",
                    "dagger.hilt.internal.aggregatedroot.codegen",
                )
                classes(
                    "*.BuildConfig",
                    "*_Factory",
                    "*_Factory\$*",
                    "*_HiltModules*",
                    "*Hilt_*",
                    "*_MembersInjector",
                    "*_GeneratedInjector",
                    "*ComposableSingletons*",
                )
                // Application bootstrap, as in core-service.
                classes("dev.inspecto.android.InspectoApp", "dev.inspecto.android.MainActivity")
            }
        }
        verify {
            rule { minBound(60) }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.androidx.datastore.preferences)

    // Photos (T-307): CameraX capture, upload queue in Room processed by WorkManager.
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    // Robolectric and Compose UI tests run on the JUnit 4 API through the vintage engine.
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.work.testing)

    // Instrumented tests on a device or emulator: Android Keystore is not available on the JVM.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

// Room schemas are kept in git: migrations of the local queue are checked against them (T-308).
ksp {
    arg("room.schemaLocation", file("schemas").path)
}
