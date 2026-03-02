# syntax=docker/dockerfile:1
FROM gradle:21-jdk-alpine
WORKDIR /app

COPY settings.gradle.kts build.gradle.kts ./
RUN /usr/bin/gradle --no-daemon installDist

COPY src ./src
RUN /usr/bin/gradle --no-daemon installDist

ENTRYPOINT ["/app/build/install/challenge/bin/challenge"]
