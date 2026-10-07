@echo off
rem Downloads the LWJGL 3.3.3 jars (skipped when already present in libs\).
setlocal
cd /d "%~dp0"
if not exist libs mkdir libs
set V=3.3.3
set BASE=https://repo1.maven.org/maven2/org/lwjgl
for %%M in (lwjgl lwjgl-glfw lwjgl-opengl) do (
    if not exist "libs\%%M-%V%.jar" curl -L --fail -o "libs\%%M-%V%.jar" "%BASE%\%%M\%V%\%%M-%V%.jar"
    for %%N in (linux macos macos-arm64 windows) do (
        if not exist "libs\%%M-%V%-natives-%%N.jar" curl -L --fail -o "libs\%%M-%V%-natives-%%N.jar" "%BASE%\%%M\%V%\%%M-%V%-natives-%%N.jar"
    )
)
echo libs ready.
