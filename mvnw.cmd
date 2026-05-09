@ECHO OFF
SET WRAPPER_JAR=.mvn\wrapper\maven-wrapper.jar
IF NOT EXIST %WRAPPER_JAR% (
  ECHO Downloading Maven Wrapper JAR...
  mkdir .mvn\wrapper 2> NUL
  curl -fsSL -o %WRAPPER_JAR% ^
    https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar
)
java -Dmaven.multiModuleProjectDirectory=%CD% -cp %WRAPPER_JAR% org.apache.maven.wrapper.MavenWrapperMain %*
