#!/usr/bin/env bash
# Downloads the LWJGL 3.3.3 jars (skipped when already present in libs/).
set -e
cd "$(dirname "$0")"
V=3.3.3
BASE=https://repo1.maven.org/maven2/org/lwjgl
mkdir -p libs
fetch() {  # fetch <module> <classifier-or-empty>
    local module=$1 class=$2
    local suffix=""
    [ -n "$class" ] && suffix="-natives-$class"
    local file="libs/$module-$V$suffix.jar"
    if [ ! -f "$file" ]; then
        echo "downloading $(basename "$file")"
        curl -L --fail --silent --show-error -o "$file" "$BASE/$module/$V/$module-$V$suffix.jar"
    fi
}
for m in lwjgl lwjgl-glfw lwjgl-opengl; do
    fetch "$m" ""
    fetch "$m" linux
    fetch "$m" macos
    fetch "$m" macos-arm64
    fetch "$m" windows
done
echo "libs ready."
