@echo off
rem Compiles VoxelCraft into out\ (requires a JDK 11+ on PATH)
setlocal
cd /d "%~dp0"
if not exist libs\lwjgl-3.3.3.jar call setup-libs.bat
where javac >nul 2>nul
if errorlevel 1 (
    echo javac not found. Install a JDK 11 or newer and add it to PATH.
    pause
    exit /b 1
)
if not exist out mkdir out
(for /f "delims=" %%f in ('dir /s /b src\*.java') do @echo "%%f") > out\sources.txt
javac --release 11 -encoding UTF-8 -cp "libs\*" -d out @out\sources.txt
if errorlevel 1 (
    echo Build failed.
    pause
    exit /b 1
)
echo Build OK -^> out\
