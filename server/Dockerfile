# syntax=docker/dockerfile:1
# Built from the repository root: docker build -f server/Dockerfile .
# libsignal-client ships native code for linux x86_64 only, so the image runs on amd64 hosts.

FROM gradle:9.8.1-jdk21-noble AS build
WORKDIR /src
COPY . .
# Configure-on-demand keeps the Android modules out of the build, so no Android SDK is needed.
# The heap is capped so the build fits on a 2 GB server.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --configure-on-demand \
        -Dorg.gradle.jvmargs=-Xmx1g \
        -Pkotlin.compiler.execution.strategy=in-process \
        :server:installDist

FROM eclipse-temurin:21.0.12.1_1-jre-noble
RUN groupadd --system blesk && useradd --system --gid blesk --home-dir /opt/blesk-server blesk \
    && mkdir -p /var/lib/blesk /srv/blesk-attachments \
    && chown blesk:blesk /var/lib/blesk /srv/blesk-attachments
COPY --from=build /src/server/build/install/blesk-server /opt/blesk-server
USER blesk
ENV BLESK_PORT=8080 \
    BLESK_FS_DIR=/srv/blesk-attachments \
    JAVA_OPTS="-XX:MaxRAMPercentage=50 -Djava.io.tmpdir=/var/lib/blesk"
EXPOSE 8080
ENTRYPOINT ["/opt/blesk-server/bin/blesk-server"]
