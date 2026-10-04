pluginManagement {
    repositories {
        // 国内网络补充源（2026-09-30 本机新增）：plugins.gradle.org 在国内不可达，
        // foojay-resolver-convention 等插件改由阿里云 Gradle 插件镜像解析
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // 工具链下载仓库（Foojay 解析器）：本地找不到匹配 JDK 时可自动下载，
    // 配合「守护进程工具链」使用（Gradle 官方推荐的工具链下载方案）
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KAgenda"
include(":app")
