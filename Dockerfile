# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies separately from source so a source-only change
# doesn't re-download the whole repository on every build.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd --system --create-home --shell /usr/sbin/nologin puduvandi
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /app/uploads && chown -R puduvandi:puduvandi /app
USER puduvandi

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
