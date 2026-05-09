#!/bin/sh
WRAPPER_JAR=".mvn/wrapper/maven-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Downloading Maven Wrapper JAR..."
  mkdir -p .mvn/wrapper
  curl -fsSL -o "$WRAPPER_JAR" \
    https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar
fi
exec java -Dmaven.multiModuleProjectDirectory="$PWD" -cp "$WRAPPER_JAR" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
