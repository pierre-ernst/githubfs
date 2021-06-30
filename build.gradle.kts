plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp-urlconnection:4.9.1")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.2")
}

group = "fr.gnodet.githubfs"
version = "1.0.0-SNAPSHOT"
description = "GitHub File System"

