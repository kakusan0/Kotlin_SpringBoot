FROM eclipse-temurin:25-jre

WORKDIR /app

# Run as non-root for better container security defaults.
RUN useradd --create-home --uid 10001 appuser

COPY target/*.jar /app/app-0.0.1-SNAPSHOT.jar
RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-XX:InitialRAMPercentage=25", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app-0.0.1-SNAPSHOT.jar"]
