@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "SELF=%~f0"
set "DIR=%~dp0"
cd /d "%DIR%"

set "MEM=2G"
set "PLAYIT=%ProgramFiles%\playit_gg\bin\playit.exe"
set "LOGFILE=%DIR%serwer-24-7.log"
set "LOCK=%DIR%serwer-24-7.lock"
set "RUNFLAG=%DIR%serwer-24-7.run"

if /i "%~1"=="__watch" goto :watch

if not "!DIR:\OneDrive=!"=="!DIR!" if exist "C:\mc-serwer\start-serwer.bat" (
  echo Zywy serwer jest w C:\mc-serwer, uruchamiam go stamtad.
  call "C:\mc-serwer\start-serwer.bat" %*
  exit /b
)

set "AUTO=0"
if /i "%~1"=="auto" set "AUTO=1"
title Serwer Minecraft 1.21.11 (24/7)

if exist "%LOGFILE%" for %%A in ("%LOGFILE%") do if %%~zA gtr 1048576 move /y "%LOGFILE%" "%DIR%serwer-24-7.old.log" >nul

( >>"%LOCK%" echo. ) 2>nul || (
  echo Nadzorca serwera juz dziala w innym oknie. Nic nie robie.
  ping -n 6 127.0.0.1 >nul
  exit /b 0
)

set "TOKEN=%RANDOM%%RANDOM%%RANDOM%"
>"%RUNFLAG%" echo !TOKEN!
start "" /b cmd /c ""!SELF!" __watch !TOKEN!" <nul >nul 2>&1

call :main 9>"%LOCK%"
set "RC=!ERRORLEVEL!"
del "%RUNFLAG%" >nul 2>&1
if "!AUTO!"=="0" (
  echo.
  echo Nadzorca zakonczyl prace.
  pause
)
exit /b !RC!

:main
call :log "Nadzorca wystartowal. Zatrzymanie serwera: wpisz stop w tym oknie."
if not "!DIR:\OneDrive=!"=="!DIR!" call :log "UWAGA: ten folder jest synchronizowany przez OneDrive, a to moze uszkodzic swiat. Lepiej uruchamiaj serwer z C:\mc-serwer."

call :findjava
if errorlevel 1 (
  call :log "BLAD: nie znaleziono Javy. Zainstaluj Jave 21 i uruchom nadzorce ponownie."
  exit /b 1
)
call :log "Java: !JAVA!"

set "QUICK=0"
:again
call :port_listening
if not errorlevel 1 (
  call :log "Port !PORT! jest juz zajety (czy serwer dziala w innym oknie?). Koncze, zeby nie robic balaganu."
  exit /b 1
)

call :log "Uruchamiam serwer..."
call :now
set "T0=!NOW_S!"
"!JAVA!" -Xmx!MEM! -jar "!DIR!fabric-server-launch.jar" nogui
set "CODE=!ERRORLEVEL!"
call :now
set /a "RAN=NOW_S-T0"
if !RAN! lss 0 set /a "RAN+=86400"
call :log "Serwer zakonczyl prace (kod !CODE!, dzialal !RAN! s)."

findstr /b /i /c:"eula=true" "!DIR!eula.txt" >nul 2>&1
if errorlevel 1 (
  call :log "Najpierw zaakceptuj EULA Minecrafta (https://aka.ms/MinecraftEULA): w pliku eula.txt zmien eula=false na eula=true i uruchom serwer ponownie."
  exit /b 0
)
if "!CODE!"=="0" (
  call :log "Zatrzymano normalnie (komenda stop). Koncze nadzor."
  exit /b 0
)
if !RAN! lss 60 (set /a "QUICK+=1") else (set "QUICK=0")
if !QUICK! geq 5 (
  call :log "Serwer pada zaraz po starcie !QUICK! razy z rzedu. Przerywam nadzor. Sprawdz logs\latest.log."
  exit /b 1
)
call :log "Awaria. Restart za 15 s (zeby przerwac, zamknij to okno)."
ping -n 16 127.0.0.1 >nul
goto :again

:watch
set "TOKEN=%~2"
set "FAILS=0"
set "MISS=0"
:watchloop
set "CUR="
if exist "%RUNFLAG%" set /p CUR=<"%RUNFLAG%"
if not "!CUR!"=="!TOKEN!" exit /b 0
call :playit_check
if errorlevel 1 (set /a "FAILS+=1") else (
  if !FAILS! gtr 0 call :log "Playit dziala."
  set "FAILS=0"
)
set "WAITN=61"
if !FAILS! geq 3 set "WAITN=301"
ping -n !WAITN! 127.0.0.1 >nul
call :port_listening
if errorlevel 1 (set /a "MISS+=1") else (set "MISS=0")
if !MISS! geq 10 exit /b 0
goto :watchloop

:log
set "LINE=%date% %time:~0,8%  %~1"
echo(!LINE!
>>"%LOGFILE%" echo(!LINE!
exit /b 0

:now
set "TT=%TIME: =0%"
set /a "NOW_S=(1%TT:~0,2%-100)*3600+(1%TT:~3,2%-100)*60+(1%TT:~6,2%-100)"
exit /b 0

:getport
set "PORT=25565"
if exist "%DIR%server.properties" for /f "tokens=2 delims==" %%p in ('findstr /b /c:"server-port=" "%DIR%server.properties"') do set "PORT=%%p"
exit /b 0

:port_listening
call :getport
netstat -ano 2>nul | findstr /r /c:":!PORT! *0\.0\.0\.0:0" >nul
exit /b %ERRORLEVEL%

:playit_check
if not exist "%PLAYIT%" exit /b 0
call "%PLAYIT%" status 2>nul | find "Phase: running" >nul
if not errorlevel 1 exit /b 0
call :log "Playit nie dziala, uruchamiam jego usluge..."
call "%PLAYIT%" start >nul 2>&1
exit /b 1

:findjava
set "JAVA="
for /f "delims=" %%j in ('where java 2^>nul') do if not defined JAVA set "JAVA=%%j"
if not defined JAVA if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if not defined JAVA exit /b 1
set "JPROPS=%DIR%serwer-24-7.java.tmp"
"!JAVA!" -XshowSettings:properties -version >nul 2>"!JPROPS!"
set "JH="
for /f "tokens=1,* delims==" %%a in ('findstr /c:"java.home" "%JPROPS%"') do set "JH=%%b"
del "!JPROPS!" >nul 2>&1
if defined JH (
  if "!JH:~0,1!"==" " set "JH=!JH:~1!"
  if exist "!JH!\bin\java.exe" set "JAVA=!JH!\bin\java.exe"
)
exit /b 0
