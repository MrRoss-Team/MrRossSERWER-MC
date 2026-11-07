@echo off
schtasks /delete /tn "EasyTP Serwer Minecraft 24-7" /f >nul 2>nul && echo Usunieto zadanie z Harmonogramu zadan.
del "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\EasyTP Serwer Minecraft 24-7.lnk" >nul 2>nul && echo Usunieto skrot z folderu Autostart.
echo Gotowe. Serwer nie bedzie juz startowal sam po zalogowaniu.
echo.
pause
