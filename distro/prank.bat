@echo off

rem
rem  Set maximum memory for JVM heap
rem
set "JAVA_OPTS= -Xmx2048m"

rem
rem  Required for Apache Arrow memory access on Java 17+
rem
set "JAVA_OPTS=%JAVA_OPTS% --add-opens=java.base/java.nio=ALL-UNNAMED"
set "JAVA_OPTS=%JAVA_OPTS% --add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
set "JAVA_OPTS=%JAVA_OPTS% --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"

rem
rem  Required for zstd-jni native library loading on Java 17+
rem
set "JAVA_OPTS=%JAVA_OPTS% --enable-native-access=ALL-UNNAMED"

rem
rem  --sun-misc-unsafe-memory-access: suppresses warnings from parquet-hadoop CleanUtil
rem  which uses sun.misc.Unsafe (upstream unfixed). Only available in Java 23+.
rem
for /f "tokens=3" %%v in ('"%JAVA_HOME%\bin\java.exe" -version 2^>^&1 ^| findstr /i version') do set "JAVA_VER=%%~v"
for /f "tokens=1 delims=." %%a in ("%JAVA_VER%") do set "JAVA_MAJOR=%%a"
if not "%JAVA_MAJOR%"=="" if %JAVA_MAJOR% GEQ 23 set "JAVA_OPTS=%JAVA_OPTS% --sun-misc-unsafe-memory-access=allow"

rem
rem  Set this to change the Java installation that will be used to run the program:
rem
rem set "JAVA_HOME=c:\java8"


set "JAVA_OPTS=%JAVA_OPTS%"

set "INSTALL_DIR=%~dp0%"
set "CLASSPATH=%INSTALL_DIR%/bin/p2rank.jar;%INSTALL_DIR%/bin/lib/*"

"%JAVA_HOME%\bin\java.exe" %JAVA_OPTS% -cp "%CLASSPATH%" cz.siret.prank.program.Main %* 