#!/bin/sh
# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup script (Unix)
#
# First-run behavior: this script will download the Maven Wrapper JAR
# (~60KB) from Maven Central into .mvn/wrapper/ and then use it to download
# the full Maven distribution.
#
# Required: Java 17 or newer on PATH (or JAVA_HOME set).
# ----------------------------------------------------------------------------

set -e

if [ -n "$JAVA_HOME" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
else
  JAVA_EXE="$(command -v java || true)"
fi
if [ -z "$JAVA_EXE" ] || [ ! -x "$JAVA_EXE" ]; then
  echo "ERROR: java not found. Install JDK 17+ or set JAVA_HOME." >&2
  exit 1
fi

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/maven-wrapper.jar"
WRAPPER_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Downloading Maven Wrapper from $WRAPPER_URL ..."
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL --retry 2 -o "$WRAPPER_JAR" "$WRAPPER_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$WRAPPER_JAR" "$WRAPPER_URL"
  else
    echo "ERROR: need curl or wget to download Maven Wrapper." >&2
    exit 1
  fi
fi

EXPECTED_SHA=$(sed -n 's/^wrapperSha256Sum=//p' "$WRAPPER_DIR/maven-wrapper.properties" | tr -d '\r')
if command -v sha256sum >/dev/null 2>&1; then
  ACTUAL_SHA=$(sha256sum "$WRAPPER_JAR" | cut -d ' ' -f 1)
elif command -v shasum >/dev/null 2>&1; then
  ACTUAL_SHA=$(shasum -a 256 "$WRAPPER_JAR" | cut -d ' ' -f 1)
else
  echo "ERROR: need sha256sum or shasum to verify Maven Wrapper." >&2
  exit 1
fi
if [ -z "$EXPECTED_SHA" ] || [ "$ACTUAL_SHA" != "$EXPECTED_SHA" ]; then
  echo "ERROR: Maven Wrapper checksum mismatch. Remove .mvn/wrapper/maven-wrapper.jar and retry." >&2
  exit 1
fi

exec "$JAVA_EXE" \
  $MAVEN_OPTS \
  -classpath "$WRAPPER_JAR" \
  "-Dmaven.multiModuleProjectDirectory=$BASE_DIR" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
