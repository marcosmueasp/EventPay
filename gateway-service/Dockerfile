FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml /app/pom.xml
# El reactor padre lista todos los módulos: hay que copiar sus poms aunque solo se compile uno.
COPY eureka-server/pom.xml /app/eureka-server/pom.xml
COPY identity-service/pom.xml /app/identity-service/pom.xml
COPY gateway-service/pom.xml /app/gateway-service/pom.xml
RUN mvn -q -pl gateway-service dependency:go-offline
COPY gateway-service/src /app/gateway-service/src
RUN mvn -q -pl gateway-service -am package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/gateway-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
