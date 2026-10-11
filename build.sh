#!/bin/sh
mkdir -p out
javac -d out -cp "lib/*" $(find src -name "*.java")
