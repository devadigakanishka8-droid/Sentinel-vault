@echo off
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -d out -cp "lib\*" @sources.txt
del sources.txt
echo Build finished.
