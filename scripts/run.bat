@echo off
REM Windows 启动脚本：scripts\run.bat [端口] [数据目录] [前端目录]
cd /d %~dp0..
java -Dfile.encoding=UTF-8 -cp bin com.researchnotes.ResearchServer %*
