FROM maven:3.9.4-eclipse-temurin-21 AS build

WORKDIR /app

# Copy the parent pom.xml
COPY pom.xml .

# Copy the entire services directory
COPY services ./services

# Run the build from the root
RUN mvn -pl services/order-service -am package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/services/order-service/target/order-service-0.0.1-SNAPSHOT.jar ./

EXPOSE 8081

CMD ["java", "-jar", "order-service-0.0.1-SNAPSHOT.jar"]
