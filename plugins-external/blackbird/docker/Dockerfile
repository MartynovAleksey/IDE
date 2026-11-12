# Builds Blackbird for IntelliJ from sources mounted at /src: JDK, Python, SD-OpenIDE to compile against and the Gradle
# dependencies are in the image; the blackbird archives come from BLACKBIRD_ARCHIVES (see ../README.md).
#
#   docker build -f docker/Dockerfile -t blackbird-intellij-build .
#   docker run --rm -v "$PWD:/src" blackbird-intellij-build [<os>...]

FROM eclipse-temurin:21-jdk AS base
ARG SD_OPENIDE_VERSION=0.0.4
RUN apt-get update && apt-get install -y --no-install-recommends python3 curl ca-certificates && rm -rf /var/lib/apt/lists/*
# the IDE the plugin compiles against, for the image's own architecture (dpkg, since the legacy builder has no TARGETARCH);
# the archive holds sd-openide/. Python unpacks it: GNU tar of Ubuntu 24.04 gets ENOSYS for every file under the legacy builder
RUN arch=$([ "$(dpkg --print-architecture)" = arm64 ] && echo arm64 || echo x64) && \
    curl -fsSL --retry 5 --retry-all-errors -o /tmp/sd-openide.tar.gz \
      "https://github.com/MartynovAleksey/IDE/releases/download/$SD_OPENIDE_VERSION/sd-openide-$SD_OPENIDE_VERSION-linux-$arch.tar.gz" && \
    python3 -c 'import tarfile; tarfile.open("/tmp/sd-openide.tar.gz").extractall("/opt", filter="tar")' && \
    rm /tmp/sd-openide.tar.gz && test -f /opt/sd-openide/build.txt
ENV GRADLE_USER_HOME=/opt/gradle
# GRADLE_USER_HOME/gradle.properties wins over the project's ideaPath (the macOS IDE); the platform cache stays out of /src
RUN mkdir -p $GRADLE_USER_HOME && printf '%s\n' \
      'ideaPath=/opt/sd-openide' \
      'org.jetbrains.intellij.platform.intellijPlatformCache=/opt/gradle/intellij-platform' \
      'kotlin.compiler.execution.strategy=in-process' \
      'org.gradle.daemon=false' > $GRADLE_USER_HOME/gradle.properties

# downloads Gradle and every dependency by building the plugin once; the sources stay in this stage only
FROM base AS warm
COPY . /warm
RUN cd /warm && ./gradlew --project-cache-dir /tmp/gradle-project test buildPlugin && rm -rf /opt/gradle/daemon

FROM base
COPY --from=warm /opt/gradle /opt/gradle
COPY docker/build.sh /usr/local/bin/build-blackbird-plugin
WORKDIR /src
ENTRYPOINT ["build-blackbird-plugin"]
