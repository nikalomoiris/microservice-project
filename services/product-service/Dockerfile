FROM maven:3.8.4-openjdk-17 AS build

WORKDIR /app

# Copy the parent pom.xml
COPY pom.xml .

# Copy the entire services directory
COPY services ./services

# Run the build from the root
RUN mvn -pl services/product-service -am package -DskipTests

FROM openjdk:17-jdk-slim

WORKDIR /app

COPY --from=build /app/services/product-service/target/product-service-0.0.1-SNAPSHOT.jar ./

EXPOSE 8080

CMD ["java", "-jar", "product-service-0.0.1-SNAPSHOT.jar"]
