# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
# unzip keeps the wrapper on the pinned .zip distribution; without it mvnw
# falls back to .tar.gz and fails the checksum (the pinned SHA-256 is the zip's).
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -Dmaven.test.skip=true

FROM eclipse-temurin:25-jre
WORKDIR /app
# chown before USER: the named volume inherits /app/data's ownership on first
# use, so the non-root process must already own it in the image.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -r -u 10001 -U app \
    && mkdir -p /app/data \
    && chown -R app:app /app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
