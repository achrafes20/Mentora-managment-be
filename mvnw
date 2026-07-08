#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script, version 3.3.2
# ----------------------------------------------------------------------------

# If JAVA_HOME is set, use it, otherwise just use java
if [ -n "$JAVA_HOME" ] ; then
  if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
    JAVACMD="$JAVA_HOME/jre/sh/java"
  else
    JAVACMD="$JAVA_HOME/bin/java"
  fi
  if [ ! -x "$JAVACMD" ] ; then
    die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
  fi
else
  JAVACMD="java"
  if ! command -v java > /dev/null 2>&1
  then
    die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
  fi
fi

# Determine the location of this script
MAVEN_WRAPPER_JAR="$(dirname "$0")/.mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES="$(dirname "$0")/.mvn/wrapper/maven-wrapper.properties"

# Read the maven wrapper url and other properties from the maven-wrapper.properties file
DISTRIBUTION_URL=$(grep distributionUrl "$MAVEN_WRAPPER_PROPERTIES" | head -1 | sed 's/distributionUrl=//')

# Download and cache the Maven Wrapper JAR if needed
if [ ! -f "$MAVEN_WRAPPER_JAR" ]; then
  WRAPPER_URL=$(grep wrapperUrl "$MAVEN_WRAPPER_PROPERTIES" | head -1 | sed 's/wrapperUrl=//')
  if [ -n "$WRAPPER_URL" ]; then
    if command -v curl > /dev/null 2>&1; then
      curl -o "$MAVEN_WRAPPER_JAR" "$WRAPPER_URL" -f -L --silent 2>/dev/null
    elif command -v wget > /dev/null 2>&1; then
      wget -q -O "$MAVEN_WRAPPER_JAR" "$WRAPPER_URL" 2>/dev/null
    fi
  fi
fi

exec "$JAVACMD" \
  $MAVEN_OPTS \
  $MAVEN_DEBUG_OPTS \
  -classpath "$MAVEN_WRAPPER_JAR" \
  "-Dmaven.multiModuleProjectDirectory=$(dirname "$0")" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
