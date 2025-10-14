FROM maven:3.8.4-openjdk-17 AS build

WORKDIR /app

# Copy the parent pom.xml
COPY pom.xml .

# Copy the entire services directory
COPY services ./services

# Run the build from the root
RUN mvn -pl services/inventory-service -am package -DskipTests

FROM openjdk:17-jdk-slim

WORKDIR /app

COPY --from=build /app/services/inventory-service/target/inventory-service-0.0.1-SNAPSHOT.jar ./

EXPOSE 8083

CMD ["java", "-jar", "inventory-service-0.0.1-SNAPSHOT.jar"]
