# Imagen de producción: compila con el JDK y ejecuta solo con el JRE, como usuario sin privilegios.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
# Las pruebas se ejecutan en CI con Testcontainers (./mvnw verify); aquí solo se empaqueta.
RUN ./mvnw -q -B -DskipTests package \
    && cp target/*.jar app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 zeal
WORKDIR /app
COPY --from=build /src/app.jar app.jar
USER zeal
EXPOSE 8083
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8083 && printf "GET /actuator/health/readiness HTTP/1.0\r\n\r\n" >&3 && grep -q "\"UP\"" <&3'
ENTRYPOINT ["java", "-jar", "app.jar"]
