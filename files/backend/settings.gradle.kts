rootProject.name = "hanseongge-api"

// Java 21 툴체인은 Gradle의 JDK 자동 탐지에 의존한다.
// 탐지 경로: JAVA_HOME, PATH, ~/.jdks (IntelliJ), SDKMAN, asdf 등
//
// JDK 21이 없는 환경에서는 아래 중 하나로 해결한다.
//   1) Temurin 21 설치 후 재빌드
//   2) gradle.properties 에 org.gradle.java.installations.paths=<JDK21 경로> 추가
//
// foojay-resolver-convention 플러그인(자동 다운로드)은 Gradle 9.5 에서
// 벤더 열거값(IBM_SEMERU) 불일치로 실패하므로 사용하지 않는다.
