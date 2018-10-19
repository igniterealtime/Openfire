@echo off

REM #
REM #

if "%JAVA_HOME%" == "" goto javaerror
if not exist "%JAVA_HOME%\bin\java.exe" goto javaerror
set OPENFIRE_HOME=%~dp0..
goto run

:javaerror
echo.
echo Error: JAVA_HOME environment variable not set, Openfire not started.
echo.
goto end

:run
if "%1" == "-debug" goto debug
start "Openfire" "%JAVA_HOME%\bin\java" -server -DopenfireHome="%OPENFIRE_HOME%" -Dopenfire.lib.dir="%OPENFIRE_HOME%\lib" -jar "%OPENFIRE_HOME%\lib\startup.jar"
goto end

:debug
start "Openfire" "%JAVA_HOME%\bin\java" -Xdebug -Xint -server -DopenfireHome="%OPENFIRE_HOME%" -Dopenfire.lib.dir="%OPENFIRE_HOME%\lib" -Xnoagent -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000 -jar "%OPENFIRE_HOME%\lib\startup.jar"
goto end
:end


