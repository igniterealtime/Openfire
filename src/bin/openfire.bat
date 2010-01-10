@echo off

REM #
REM # $RCSfile$
REM # $Revision: 1102 $
REM # $Date: 2005-03-07 22:36:48 -0300 (Mon, 07 Mar 2005) $
REM #

if "%JAVA_HOME%" == "" goto javaerror
if not exist "%JAVA_HOME%\bin\java.exe" goto javaerror
set OPENFIRE_HOME=%CD%\..
goto run

:javaerror
echo.
echo Error: JAVA_HOME environment variable not set, Openfire not started.
echo.
goto end

:run
if "%1" == "-debug" goto debug
start "Openfire" "%JAVA_HOME%\bin\java" -server -DopenfireHome="%OPENFIRE_HOME%" -jar ..\lib\startup.jar
goto end

:debug
start "Openfire" "%JAVA_HOME%\bin\java" -Xdebug -Xint -server -DopenfireHome="%OPENFIRE_HOME%" -Xnoagent -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000 -jar ..\lib\startup.jar
goto end
:end


