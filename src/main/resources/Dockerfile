FROM multiarch/qemu-user-static AS qemu_installer
FROM openjdk:17-jdk-slim

COPY --from=qemu_installer /usr/bin/qemu-aarch64-static /usr/bin/

WORKDIR /app
COPY target/exception-retry-example-1.0-SNAPSHOT.jar /app/exception-retry-example-1.0-SNAPSHOT.jar
RUN mkdir -p /agent && chown -R 1000:1000 /agent
USER 1000
ENTRYPOINT ["java", "-jar", "exception-retry-example-1.0-SNAPSHOT.jar"]