@echo off
REM Windows 编译脚本（需要 JDK 8+）
cd /d %~dp0..
if not exist bin mkdir bin
dir /s /b src\*.java > bin\sources.txt
javac -encoding UTF-8 -d bin @bin\sources.txt
del bin\sources.txt
echo 编译完成：bin\
