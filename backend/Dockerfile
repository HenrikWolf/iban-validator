FROM eclipse-temurin:21-jre

WORKDIR /app
COPY target/*.jar app.jar
USER 1001

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
