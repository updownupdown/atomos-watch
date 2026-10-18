// Used only when core is built on its own (`gradle -p core test`), e.g. without the Android SDK.
// The root build ignores this file.
pluginManagement {
    repositories { mavenCentral(); gradlePluginPortal() }
    plugins { id("org.jetbrains.kotlin.jvm") version "2.0.21" }
}
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "core"
