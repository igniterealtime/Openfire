@echo off

SETLOCAL

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
SET debug=
FOR %%A IN (%*) DO (
  IF "%%A"=="-debug" SET debug=-Xdebug -Xint -Xnoagent -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000
  IF "%%A"=="-demoboot" (
    echo Starting demoboot
    copy "%OPENFIRE_HOME%\conf\openfire-demoboot.xml" "%OPENFIRE_HOME%\conf\openfire.xml"
  )
  IF "%%A"=="-devboot" (
    echo Starting devboot
    powershell -Command "(Get-Content '%OPENFIRE_HOME%\conf\openfire-demoboot.xml') -replace 'example.org', '%COMPUTERNAME%' | Set-Content '%OPENFIRE_HOME%\conf\openfire.xml'"
  )
)
start "Openfire" "%JAVA_HOME%\bin\java" %debug% -server -Djdk.tls.ephemeralDHKeySize=matched -Djsse.SSLEngine.acceptLargeFragments=true -Djava.net.preferIPv6Addresses=system -DopenfireHome="%OPENFIRE_HOME%" -Dlog4j.configurationFile="%OPENFIRE_HOME%\lib\log4j2.xml" -Dlog4j2.formatMsgNoLookups=true -Dlog4j.skipJansi=false -Dopenfire.lib.dir="%OPENFIRE_HOME%\lib" -jar "%OPENFIRE_HOME%\lib\startup.jar"
goto end

:end


