@echo off
rem Avatar Studio mit Konsole starten: alle Meldungen bleiben stehen (zur Fehlersuche)
cd /d "%~dp0"
if not exist dist\Avatar.jar (echo dist\Avatar.jar fehlt: bitte in NetBeans Clean and Build ausfuehren. ^& pause ^& exit /b 1)
java -Xmx4g -Dsun.java2d.uiScale.enabled=true --enable-native-access=ALL-UNNAMED --add-exports java.base/java.lang=ALL-UNNAMED --add-exports java.desktop/sun.awt=ALL-UNNAMED --add-exports java.desktop/sun.java2d=ALL-UNNAMED -jar dist\Avatar.jar
echo.
echo Avatar Studio beendet (Rueckgabewert %errorlevel%).
pause
