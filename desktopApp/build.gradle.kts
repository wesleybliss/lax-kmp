import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.gammagamma.lax.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Lax"
            packageVersion = "0.1.0"
            description = "A minimal native Slack desktop client"
            copyright = "Copyright 2026 GAMMA GAMMA"
            vendor = "GAMMA GAMMA"
            linux {
                packageName = "lax"
                debMaintainer = "gammagammaco@gmail.com"
                menuGroup = "Network"
                appCategory = "Network"
            }
        }
    }
}
