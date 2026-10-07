@echo off
rem Builds (if needed) and runs SkyTrainer.
setlocal
cd /d "%~dp0"
if not exist out\skytrainer\Main.class call build.bat
java -Xmx2G -cp "libs\*;out" skytrainer.Main %*
pause
