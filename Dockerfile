FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/*.jar /app/app-0.0.1-SNAPSHOT.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app-0.0.1-SNAPSHOT.jar"]
