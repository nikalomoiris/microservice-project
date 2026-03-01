FROM maven:3.9.4-eclipse-temurin-21 AS build

WORKDIR /app

# Copy the parent pom.xml
COPY pom.xml .

# Copy the entire services directory
COPY services ./services

# Run the build from the root
RUN mvn -pl services/payment-service -am package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/services/payment-service/target/payment-service-0.0.1-SNAPSHOT.jar ./

EXPOSE 8084

CMD ["java", "-jar", "payment-service-0.0.1-SNAPSHOT.jar"]