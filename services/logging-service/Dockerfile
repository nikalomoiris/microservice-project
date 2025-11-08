FROM maven:3.8.4-openjdk-17 AS build

WORKDIR /app

# Copy parent pom
COPY pom.xml .
COPY services ./services

# Build only the logging-service module
RUN mvn -pl services/logging-service -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/services/logging-service/target/logging-service-0.0.1-SNAPSHOT.jar ./

EXPOSE 8090
CMD ["java", "-jar", "logging-service-0.0.1-SNAPSHOT.jar"]
