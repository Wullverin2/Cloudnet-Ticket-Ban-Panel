plugins {
  java
}

group = "de.speed"
version = "0.1.0-SNAPSHOT"

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
  withSourcesJar()
}

repositories {
  mavenCentral()
}

dependencies {
  compileOnly("eu.cloudnetservice.cloudnet:driver-api:4.0.0-RC16")
  compileOnly("eu.cloudnetservice.cloudnet:node-api:4.0.0-RC16")
  compileOnly("org.slf4j:slf4j-api:2.0.16")
}

tasks.withType<JavaCompile>().configureEach {
  options.encoding = "UTF-8"
  options.release.set(21)
}

tasks.jar {
  archiveBaseName.set("TicketConsoleCloudBan")
}
