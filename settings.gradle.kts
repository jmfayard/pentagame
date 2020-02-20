import de.fayard.versions.bootstrapRefreshVersions
import de.fayard.dependencies.DependenciesPlugin

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven(url = "https://jcenter.bintray.com/")
        maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
    }

    resolutionStrategy {
        eachPlugin {
            val module = when(requested.id.id) {
                "kotlinx-serialization" -> "org.jetbrains.kotlin:kotlin-serialization:${requested.version}"
                "proguard" -> "net.sf.proguard:proguard-gradle:${requested.version}"
                else -> null
            }
            if(module != null) {
                useModule(module)
            }
        }
    }
}

plugins {
  id("com.gradle.enterprise").version("3.1.1")
}

buildscript {
    dependencies.classpath("de.fayard:dependencies:0.5.6")
}

bootstrapRefreshVersions(DependenciesPlugin.artifactVersionKeyRules)

enableFeaturePreview("GRADLE_METADATA")

//includeBuild("ksvg")

include("backend")
include("frontend")
include("shared")
include(":muirwik")
//include(":ksvg")

//project(":ksvg").projectDir = rootDir.resolve("ksvg")
project(":muirwik").projectDir = rootDir.resolve("muirwik/muirwik-components")


gradleEnterprise {
    buildScan {
        termsOfServiceAgree = "yes"
//        publishAlwaysIf(true)
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
    }
}