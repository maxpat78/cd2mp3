/*
 * This file was generated by the Gradle 'init' task.
 *
 * This is a general purpose Gradle build.
 * Learn more about Gradle by exploring our samples at https://docs.gradle.org/7.0-rc-1/samples
 */
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
plugins {
   java
   application
   id("com.github.johnrengelman.shadow") version "6.1.0"
}

java {                                      
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    //sourceCompatibility = JavaVersion.VERSION_11
    //targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    mavenCentral()
}

dependencies {
	implementation(group = "commons-cli", name = "commons-cli", version = "1.4")
}

application {
    mainClass.set("cd2mp3") 
    mainClassName = "cd2mp3"
}

tasks.withType<ShadowJar>() {
    manifest {
        attributes["Main-Class"] = "cd2mp3"
        attributes["Implementation-Name"] = "cd2mp3"
   }
}