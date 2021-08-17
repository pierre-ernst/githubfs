plugins {
    java
}

group = "com.github.pierre_ernst"
version = "0.0.3-PERNST"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.kohsuke:github-api:1.131")
    implementation("com.squareup.okhttp3:okhttp:3.14.9")
    implementation("com.github.package-url:packageurl-java:1.3.1")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.2")
}
