FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml /app/pom.xml
# El reactor padre lista todos los módulos: hay que copiar sus poms aunque solo se compile uno.
COPY eureka-server/pom.xml /app/eureka-server/pom.xml
COPY identity-service/pom.xml /app/identity-service/pom.xml
COPY gateway-service/pom.xml /app/gateway-service/pom.xml
COPY booking-service/pom.xml /app/booking-service/pom.xml
RUN mvn -q -pl eureka-server dependency:go-offline
COPY eureka-server/src /app/eureka-server/src
RUN mvn -q -pl eureka-server -am package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/eureka-server/target/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
