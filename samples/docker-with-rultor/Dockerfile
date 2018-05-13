# Build stage
FROM g4s8/alpine:jdk-8 as build
# Version argument (should be passed by Rultor)
ARG version="1.0-SNAPSHOT"
WORKDIR /build
# Copying project
COPY pom.xml ./pom.xml
COPY src ./src
# Update project vesion to $version argument and build a jar
RUN mvn versions:set -DnewVersion=${version} install -Pdocker

# Run stage
FROM g4s8/alpine:jre-8
WORKDIR /app
# Copy build from build-stage layer
COPY --from=build /build/target/app.jar /app/app.jar
COPY --from=build /build/target/deps /app/deps
# Run main class
ENTRYPOINT ["java"]
EXPOSE 80
CMD ["-cp", "app.jar:deps/*", "com.g4s8.sample.App", "--port=80"]
