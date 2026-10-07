#!/usr/bin/env bash
# Builds (if needed) and runs SkyTrainer.
set -e
cd "$(dirname "$0")"
if [ ! -f out/skytrainer/Main.class ] || [ -n "$(find src -name '*.java' -newer out/skytrainer/Main.class 2>/dev/null)" ]; then
    bash build.sh
fi
# macOS needs GLFW on the main thread or it crashes at startup
JVM_ARGS="-Xmx2G"
case "$(uname -s)" in
    Darwin) JVM_ARGS="$JVM_ARGS -XstartOnFirstThread" ;;
esac
java $JVM_ARGS -cp "libs/*:out" skytrainer.Main "$@"
