#!/usr/bin/env bash
# Compiles SkyTrainer sources into out/ using javac (--release 11).
set -e
cd "$(dirname "$0")"
mkdir -p out
find src -name '*.java' > out/sources.txt
javac --release 11 -cp "libs/*" -d out @out/sources.txt
echo "Build OK -> out/"
