#!/bin/sh
# Maven Wrapper script
MAVEN_WRAPPER_JAR=".mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES=".mvn/wrapper/maven-wrapper.properties"

if [ -r "$MAVEN_WRAPPER_PROPERTIES" ]; then
  DISTRIBUTION_URL=$(grep 'distributionUrl' "$MAVEN_WRAPPER_PROPERTIES" | cut -d= -f2-)
fi

exec mvn "$@"
