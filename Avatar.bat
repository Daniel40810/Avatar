@echo off
rem Avatar Studio ohne NetBeans starten (nach Clean and Build)
cd /d "%~dp0"
if not exist dist\Avatar.jar (echo dist\Avatar.jar fehlt: bitte in NetBeans Clean and Build ausfuehren. ^& pause ^& exit /b 1)
start "" javaw -Xmx4g -Dsun.java2d.uiScale.enabled=true --enable-native-access=ALL-UNNAMED --add-exports java.base/java.lang=ALL-UNNAMED --add-exports java.desktop/sun.awt=ALL-UNNAMED --add-exports java.desktop/sun.java2d=ALL-UNNAMED -jar dist\Avatar.jar
