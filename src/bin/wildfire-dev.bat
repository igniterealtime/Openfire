@echo off

REM #
REM # $RCSfile$
REM # $Revision: 1102 $
REM # $Date: 2005-03-07 22:36:48 -0300 (Mon, 07 Mar 2005) $
REM #

if "%JAVA_HOME%" == "" goto javaerror
if not exist "%JAVA_HOME%\bin\java.exe" goto javaerror
goto run

:javaerror
echo.
echo Error: JAVA_HOME environment variable not set, Wildfire not started.
echo.
goto end

:run
if "%1" == "-debug" goto debug
start "Wildfire" "%JAVA_HOME%\bin\java" -DdevelopmentMode="true" -server -cp "%JAVA_HOME%\lib\tools.jar;..\..\build\lib\ant.jar;..\..\build\lib\ant-contrib.jar;..\lib\activation.jar;..lib\bouncycastle.jar;..\lib\commons-el.jar;..\lib\hsqldb.jar;..\lib\jasper-compiler.jar;..\lib\jasper-runtime.jar;..\lib\jtds.jar;..\lib\mail.jar;..\lib\mysql.jar;..\lib\postgres.jar;..\lib\servlet.jar;..\lib\startup.jar;..\lib\wildfire.jar" org.jivesoftware.wildfire.starter.ServerStarter
goto end

:debug
start "Wildfire" "%JAVA_HOME%\bin\java" -Xdebug -Xint -Xnoagent -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000 -DdevelopmentMode="true" -server -cp "%JAVA_HOME%\lib\tools.jar;..\..\build\lib\ant.jar;..\..\build\lib\ant-contrib.jar;..\lib\activation.jar;..lib\bouncycastle.jar;..\lib\commons-el.jar;..\lib\hsqldb.jar;..\lib\jasper-compiler.jar;..\lib\jasper-runtime.jar;..\lib\jtds.jar;..\lib\mail.jar;..\lib\mysql.jar;..\lib\postgres.jar;..\lib\servlet.jar;..\lib\startup.jar;..\lib\wildfire.jar" org.jivesoftware.wildfire.starter.ServerStarter
goto end
:end


