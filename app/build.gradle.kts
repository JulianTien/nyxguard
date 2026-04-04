import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun readConfigProperty(name: String): String? {
    val fromGradle = providers.gradleProperty(name).orNull?.trim()
    if (!fromGradle.isNullOrEmpty()) {
        return fromGradle
    }
    val fromLocal = localProperties.getProperty(name)?.trim()
    return fromLocal?.takeIf { it.isNotEmpty() }
}

fun normalizeBaseUrl(value: String): String = if (value.endsWith("/")) value else "$value/"

val defaultLocalApiBaseUrl = "http://10.0.2.2:5001/"

val requestedTasks = gradle.startParameter.taskNames.map { it.lowercase() }
val requestedTaskText = requestedTasks.joinToString(" ")

val localApiBaseUrl = readConfigProperty("nyxGuardLocalApiBaseUrl")?.let(::normalizeBaseUrl)
val stagingApiBaseUrl = readConfigProperty("nyxGuardStagingApiBaseUrl")?.let(::normalizeBaseUrl)
val prodApiBaseUrl = readConfigProperty("nyxGuardProdApiBaseUrl")?.let(::normalizeBaseUrl)

fun requireApiBaseUrl(env: String, propertyName: String, value: String?) {
    if (value.isNullOrBlank()) {
        error(
            "Missing $propertyName for $env builds. " +
                "Set it in ~/.gradle/gradle.properties or local.properties."
        )
    }
}

if (requestedTaskText.contains("staging")) {
    requireApiBaseUrl("staging", "nyxGuardStagingApiBaseUrl", stagingApiBaseUrl)
}
if (requestedTaskText.contains("prod")) {
    requireApiBaseUrl("prod", "nyxGuardProdApiBaseUrl", prodApiBaseUrl)
}

android {
    namespace = "com.scf.nyxguard"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.scf.nyxguard"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "env"
    productFlavors {
        create("local") {
            dimension = "env"
            applicationIdSuffix = ".local"
            buildConfigField("String", "NYXGUARD_ENV", "\"local\"")
            buildConfigField(
                "String",
                "NYXGUARD_API_BASE_URL",
                "\"${localApiBaseUrl ?: defaultLocalApiBaseUrl}\""
            )
            buildConfigField("boolean", "NYXGUARD_ENABLE_DEBUG_MOCK_FALLBACK", "true")
        }
        create("staging") {
            dimension = "env"
            applicationIdSuffix = ".staging"
            buildConfigField("String", "NYXGUARD_ENV", "\"staging\"")
            buildConfigField(
                "String",
                "NYXGUARD_API_BASE_URL",
                "\"${stagingApiBaseUrl ?: "https://invalid-staging.local/"}\""
            )
            buildConfigField("boolean", "NYXGUARD_ENABLE_DEBUG_MOCK_FALLBACK", "true")
        }
        create("prod") {
            dimension = "env"
            buildConfigField("String", "NYXGUARD_ENV", "\"prod\"")
            buildConfigField(
                "String",
                "NYXGUARD_API_BASE_URL",
                "\"${prodApiBaseUrl ?: "https://invalid-prod.local/"}\""
            )
            buildConfigField("boolean", "NYXGUARD_ENABLE_DEBUG_MOCK_FALLBACK", "false")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

androidComponents {
    beforeVariants(selector().all()) { variantBuilder ->
        val envName = variantBuilder.productFlavors.toMap()["env"]
        if (variantBuilder.buildType == "release" && envName != "prod") {
            variantBuilder.enable = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.0")
    implementation("com.facebook.shimmer:shimmer:0.5.0")
    
    // 高德地图 SDK（合并包：3D地图 + 定位 + 搜索）
    implementation("com.amap.api:3dmap-location-search:10.1.600_loc6.5.1_sea9.7.4")
    
    // 网络请求
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit-mock:2.11.0")
    
    // ViewModel & LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Glide图片加载
    implementation("com.github.bumptech.glide:glide:4.16.0")
    
    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // MockWebServer for testing
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
