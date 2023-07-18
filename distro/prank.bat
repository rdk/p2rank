@echo off

rem
rem  Set maximum memory for JVM heap
rem
set "JAVA_OPTS= -Xmx2048m"

rem
rem  Set this to change the Java installation that will be used to run the program:
rem
rem set "JAVA_HOME=c:\java8"


set "JAVA_OPTS=%JAVA_OPTS%"

set "INSTALL_DIR=%~dp0%"
set "CLASSPATH=%INSTALL_DIR%/bin/p2rank.jar;%INSTALL_DIR%/bin/lib/*"

"%JAVA_HOME%\bin\java.exe" %JAVA_OPTS% -cp "%CLASSPATH%" cz.siret.prank.program.Main %* 