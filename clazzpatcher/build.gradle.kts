// clazzpatcher (© автор WexManager): DEX ⇄ smali, .class ⇄ csmali, class ⇄ dex — чистый Kotlin без зависимостей.
// Источник: ~/Tools/clazzpatcher-two (копия исходников). Используется DEX-редактором и просмотром .class.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
