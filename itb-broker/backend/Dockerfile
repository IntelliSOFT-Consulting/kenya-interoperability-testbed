FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# 17-jre-alpine has no arm64 manifest; the plain jre tag is multi-arch
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /app/certificates
COPY --from=builder /app/target/itb-broker-*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java","-jar","app.jar"]
