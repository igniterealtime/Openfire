@echo off

REM #
REM # $RCSfile$
REM # $Revision$
REM # $Date$
REM #

if "%JAVA_HOME%" == "" goto javaerror
if not exist "%JAVA_HOME%\bin\java.exe" goto javaerror
goto run

:javaerror
echo.
echo Error: JAVA_HOME environment variable not set, Jive Messenger not started.
echo.
goto end

:run
start "Jive Messenger" "%JAVA_HOME%\bin\java" -jar ..\lib\startup.jar
goto end

:end