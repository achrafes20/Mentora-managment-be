@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM Apache Maven Wrapper startup batch script, version 3.3.2

@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET @@MVNW_JAVA_HOME=%JAVA_HOME%

@SET MAVEN_WRAPPER_JAR=%~dp0.mvn\wrapper\maven-wrapper.jar
@SET MAVEN_WRAPPER_PROPERTIES=%~dp0.mvn\wrapper\maven-wrapper.properties

@FOR /F "usebackq tokens=1,* delims==" %%A IN ("%MAVEN_WRAPPER_PROPERTIES%") DO (
    @IF "%%A"=="distributionUrl" SET DISTRIBUTION_URL=%%B
    @IF "%%A"=="wrapperUrl" SET WRAPPER_URL=%%B
)

@IF NOT EXIST "%MAVEN_WRAPPER_JAR%" (
    @IF NOT "%WRAPPER_URL%"=="" (
        @curl -o "%MAVEN_WRAPPER_JAR%" "%WRAPPER_URL%" --silent --fail --location 2>NUL
        @IF NOT "%ERRORLEVEL%"=="0" (
            @echo Could not download maven wrapper. Using maven from PATH if available.
        )
    )
)

@IF EXIST "%JAVA_HOME%\bin\java.exe" (
    SET JAVACMD="%JAVA_HOME%\bin\java.exe"
) ELSE (
    SET JAVACMD=java.exe
)

%JAVACMD% %MAVEN_OPTS% %MAVEN_DEBUG_OPTS% -classpath "%MAVEN_WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%~dp0" org.apache.maven.wrapper.MavenWrapperMain %*
