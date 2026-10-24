plugins {
  id("org.jetbrains.kotlin.jvm") version "2.4.0"
  id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "io.sdopenide"

repositories {
  mavenCentral()
  intellijPlatform { defaultRepositories() }
}

kotlin { jvmToolchain(21) }

dependencies {
  intellijPlatform {
    // the plugin is built for SD-OpenIDE: compile against an installed one (ideaPath in gradle.properties or -PideaPath=...)
    local(providers.gradleProperty("ideaPath"))
    bundledPlugin("org.jetbrains.plugins.terminal")
  }
}

intellijPlatform {
  pluginConfiguration {
    ideaVersion {
      sinceBuild = "262"
      untilBuild = "262.*"
    }
  }
  // the Hermes payload is added per OS by payload/package_plugin.py; there is nothing to search or verify here
  buildSearchableOptions = false
}
