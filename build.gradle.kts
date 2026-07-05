plugins {
    id("com.android.application").apply(false)
    alias(libs.plugins.kotlinSerialization).apply(false)
    alias(libs.plugins.ksp).apply(false)
    alias(libs.plugins.detekt).apply(false)
}

tasks.register<Delete>("clean") {
    delete {
        rootProject.buildDir
    }
}
