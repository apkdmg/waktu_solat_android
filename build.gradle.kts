import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `maven-publish`
}

group = "com.github.apkdmg"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    api(libs.okhttp)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform {
        // Live API tests hit the network; run them with `./gradlew liveTest`.
        excludeTags("live")
    }
}

tasks.register<Test>("liveTest") {
    description = "Runs tests against the real api.waktusolat.app."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("live") }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])
            artifactId = "waktu_solat_android"
            pom {
                name.set("WaktuSolatAndroid")
                description.set("Malaysia prayer times (waktu solat) API client for Android and Kotlin/JVM.")
                url.set("https://github.com/apkdmg/waktu_solat_android")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                scm {
                    url.set("https://github.com/apkdmg/waktu_solat_android")
                }
            }
        }
    }
}
