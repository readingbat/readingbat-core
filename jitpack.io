jdk:
  - openjdk11
install:
  - ./gradlew build -xtest
  - ./gradlew publishToMavenLocal

