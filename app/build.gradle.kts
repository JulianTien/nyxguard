import java.util.Properties
import java.net.URI

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

fun normalizeAndroidDebugBaseUrl(value: String): String {
    val normalized = normalizeBaseUrl(value)
    val uri = runCatching { URI(normalized) }.getOrNull() ?: return normalized
    val host = uri.host?.lowercase() ?: return normalized
    if (host != "127.0.0.1" && host != "localhost") {
        return normalized
    }

    val port = if (uri.port >= 0) ":${uri.port}" else ""
    val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
    val query = uri.rawQuery?.let { "?$it" } ?: ""
    val fragment = uri.rawFragment?.let { "#$it" } ?: ""
    return "${uri.scheme}://10.0.2.2$port$path$query$fragment"
}

val defaultApiBaseUrl = "https://nyxguard.vercel.app/"
val sharedApiBaseUrl = (
    readConfigProperty("nyxGuardApiBaseUrl")
        ?: readConfigProperty("nyxGuardProdApiBaseUrl")
        ?: readConfigProperty("nyxGuardStagingApiBaseUrl")
        ?: readConfigProperty("nyxGuardLocalApiBaseUrl")
        ?: defaultApiBaseUrl
).let(::normalizeBaseUrl)

val debugApiBaseUrl = (
    readConfigProperty("nyxGuardLocalApiBaseUrl")
        ?: readConfigProperty("nyxGuardApiBaseUrl")
        ?: readConfigProperty("nyxGuardStagingApiBaseUrl")
        ?: readConfigProperty("nyxGuardProdApiBaseUrl")
        ?: defaultApiBaseUrl
).let(::normalizeAndroidDebugBaseUrl)

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
        buildConfigField("String", "NYXGUARD_ENV", "\"default\"")
        buildConfigField("String", "NYXGUARD_API_BASE_URL", "\"$sharedApiBaseUrl\"")
        buildConfigField("boolean", "NYXGUARD_ENABLE_DEBUG_MOCK_FALLBACK", "false")
    }

    buildTypes {
        debug {
            buildConfigField("String", "NYXGUARD_API_BASE_URL", "\"$debugApiBaseUrl\"")
        }
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.0")
    implementation("com.facebook.shimmer:shimmer:0.5.0")
    
    // 高德官方当前推荐的组合依赖，已包含 3D 地图、定位和搜索能力。
    implementation("com.amap.api:3dmap-location-search:10.1.200_loc6.4.9_sea9.7.4")
    
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

    // Firebase Cloud Messaging（无 google-services 配置时会自动降级为空操作）
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation("com.google.firebase:firebase-messaging")
    
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
