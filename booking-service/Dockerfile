FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml /app/pom.xml
# El reactor padre lista todos los módulos: hay que copiar sus poms aunque solo se compile uno.
COPY eureka-server/pom.xml /app/eureka-server/pom.xml
COPY identity-service/pom.xml /app/identity-service/pom.xml
COPY gateway-service/pom.xml /app/gateway-service/pom.xml
COPY booking-service/pom.xml /app/booking-service/pom.xml
RUN mvn -q -pl booking-service dependency:go-offline
COPY booking-service/src /app/booking-service/src
RUN mvn -q -pl booking-service -am package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/booking-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
