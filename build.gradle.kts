plugins {
    java
}

allprojects {
    group = "ru.vikulinva.orderservice"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
    }

    // Lombok одинаково во всех модулях (JS-6.6). На VO / Entity / Aggregate он не
    // используется (records / ручные классы) — но handler'ам / сервисам / событиям нужен.
    dependencies {
        "compileOnly"("org.projectlombok:lombok:1.18.34")
        "annotationProcessor"("org.projectlombok:lombok:1.18.34")
        "testCompileOnly"("org.projectlombok:lombok:1.18.34")
        "testAnnotationProcessor"("org.projectlombok:lombok:1.18.34")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showExceptions = true
            showStackTraces = true
            showCauses = true
        }
        // Передаём Docker-сокет в форкнутый JVM (Testcontainers на macOS с
        // Docker Desktop часто не находит сокет автоматически). Можно
        // переопределить через -PdockerHost=... в gradle.properties.
        val explicitHost = (findProperty("dockerHost") as String?) ?: System.getenv("DOCKER_HOST")
        val resolvedHost = explicitHost ?: listOf(
            "${System.getProperty("user.home")}/.docker/run/docker.sock",
            "/var/run/docker.sock"
        ).firstOrNull { file(it).exists() }?.let { "unix://$it" }
        resolvedHost?.let { host ->
            environment("DOCKER_HOST", host)
            systemProperty("docker.host", host)
        }
    }

    repositories {
        mavenCentral()
        mavenLocal()
    }
}
