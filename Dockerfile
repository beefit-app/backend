# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre-noble
RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home beefit
WORKDIR /app
COPY --from=build --chown=beefit:beefit /workspace/target/beefit-api-*.jar app.jar
USER 10001
EXPOSE 8080
# Datas LocalDateTime (como Post.publishedAt, via @CreationTimestamp) usam o fuso da
# JVM e vão para o cliente sem offset; em UTC o app mostraria a hora 3h adiantada.
# Precisa acompanhar beefit.business.time-zone: o ENTRYPOINT exec não expande variável.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Duser.timezone=America/Sao_Paulo", "-jar", "/app/app.jar"]
