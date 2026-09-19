plugins {
    java
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.hanseongge"
version = "0.1.0"
description = "한성금거래소 쇼핑몰 API"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly { extendsFrom(configurations.annotationProcessor.get()) }
}

repositories {
    mavenCentral()
}

val querydslVersion = "5.1.0"
val springdocVersion = "2.8.6"
val jjwtVersion = "0.12.6"
val bucket4jVersion = "8.14.0"
val zonkyPostgresVersion = "2.1.0"
val embeddedPgBinariesVersion = "16.9.0"

dependencies {
    // --- Web / Validation ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // --- Security ---
    implementation("org.springframework.boot:spring-boot-starter-security")

    // --- AOP (@AuditLog 감사 기록 어드바이스) ---
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // --- JWT (STEP 5, 관리자 토큰 aud=admin) ---
    // api 만 컴파일에 노출한다. impl/jackson 이 컴파일 클래스패스에 있으면
    // 내부 구현 클래스를 직접 참조하는 코드가 실수로 들어올 수 있다.
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    // --- Rate Limit (로그인 엔드포인트, 설계서 §10 보안 체크리스트) ---
    implementation("com.bucket4j:bucket4j_jdk17-core:$bucket4jVersion")

    // --- SEED 암호 (KG이니시스 통합인증 결과 복호화) ---
    // SEED 는 JDK 에 없는 국산 블록 암호다. 이니시스 샘플은 KISA 소스를 통째로 복사해
    // 쓰지만, 수백 줄짜리 암호 코드를 저장소에 들고 있는 것보다 관리되는 구현을 쓴다.
    // 호환성은 KISA 공식 코드로 만든 암호문으로 고정한다 (InicisSeedCipherTest).
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")

    // --- Persistence ---
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // --- QueryDSL (jakarta) ---
    implementation("com.querydsl:querydsl-jpa:$querydslVersion:jakarta")
    annotationProcessor("com.querydsl:querydsl-apt:$querydslVersion:jakarta")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")

    // --- 캐시 (시세/설정값 캐싱, 설계서 §7.3 — Redis는 트래픽 발생 후) ---
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // --- API 문서 ---
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    // --- 운영 지표 ---
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // --- Lombok ---
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // --- Test ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // 실제 PostgreSQL 16 바이너리를 내려받아 구동한다. Docker가 필요 없다.
    // (프로젝트 결정상 Docker 미사용 → Testcontainers를 쓸 수 없어 이 방식을 택한다)
    // V1~V5 가 JSONB·plpgsql 트리거·부분 UNIQUE 인덱스를 쓰므로 H2 로는 검증이 불가능하다.
    testImplementation("io.zonky.test:embedded-postgres:$zonkyPostgresVersion")
    testImplementation(
        enforcedPlatform("io.zonky.test.postgres:embedded-postgres-binaries-bom:$embeddedPgBinariesVersion")
    )
    // 실행 OS 바이너리는 명시적으로 넣어야 한다. CI(Linux)로 옮길 때 linux-amd64 를 추가할 것.
    testRuntimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-windows-amd64")
}

// QueryDSL Q-클래스 생성 위치 (build/ 하위 → 형상관리 대상 아님)
val generatedDir = layout.buildDirectory.dir("generated/querydsl")

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.generatedSourceOutputDirectory = generatedDir.get().asFile
}

tasks.named<Delete>("clean") {
    delete(generatedDir)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // 테스트는 임베디드 PostgreSQL을 쓴다. 운영 DB 접속 사고를 구조적으로 막는다.
    systemProperty("spring.profiles.active", "test")
    testLogging {
        events("passed", "failed", "skipped")
    }
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // 프로파일 지정 우선순위: -Dspring.profiles.active > SPRING_PROFILES_ACTIVE > local
    //
    // PowerShell은 -D 인자를 분해하므로 환경변수 방식을 권장한다.
    //   PowerShell : $env:SPRING_PROFILES_ACTIVE="dev"; .\gradlew.bat bootRun
    //   bash       : SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
    val profile = System.getProperty("spring.profiles.active")
        ?: System.getenv("SPRING_PROFILES_ACTIVE")
        ?: "local"
    systemProperty("spring.profiles.active", profile)
}

tasks.named<Jar>("jar") {
    // plain jar 는 배포에 쓰지 않는다
    enabled = false
}
