#!/bin/sh
# Runs in the image from Dockerfile: tests and builds the plugin from /src, then writes /src/build/dist/blackbird-intellij-<ver>-<os>.zip
# for the given OS names (all six by default) with the blackbird archives from BLACKBIRD_ARCHIVES.
set -eu
cd /src
version=$(sed -n 's/^version=//p' gradle.properties)
# the project cache stays out of /src, where the host's Gradle keeps its own
./gradlew --offline --project-cache-dir /tmp/gradle-project test buildPlugin
rm -rf build/dist
python3 package_plugin.py "build/distributions/blackbird-intellij-$version.zip" build/dist "$@"
(cd build/dist && sha256sum *.zip > SHA256SUMS.txt)
