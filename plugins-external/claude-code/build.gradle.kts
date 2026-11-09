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
    // compile against an installed IDE (ideaPath in gradle.properties or -PideaPath=...)
    local(providers.gradleProperty("ideaPath"))
    bundledPlugin("org.jetbrains.plugins.terminal")
  }
  testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
  pluginConfiguration {
    ideaVersion {
      sinceBuild = "262"
      untilBuild = "262.*"
    }
  }
  // nothing to search or verify here
  buildSearchableOptions = false
}
