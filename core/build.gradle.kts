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
    jvmTarget = JvmTarget.JVM_17
  }
}

dependencies {
  // Android ships the XmlPull API; the JVM tests use kXML, the same parser Android is built on.
  compileOnly(libs.xmlpull)
  testImplementation(libs.xmlpull)
  testImplementation(libs.kxml2)
  testImplementation(libs.junit)
}
