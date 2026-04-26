plugins {
    `java-library`
}

dependencies {
    api(project(":core"))

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.kafka)
    implementation(libs.usecase.pattern.starter)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
}
