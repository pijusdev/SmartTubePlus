@echo off
chcp 65001 >nul
set JAVA_HOME=C:\jdk\jdk-17.0.20.1+1
cd /d D:\cc\workspacendroid\SmartTube
REM Build RELEASE (smak ststable) - do uczciwego porownania z oryginalem.
REM Podpis: keystore.properties wskazuje na debug.keystore - APK wchodzi na wierzch.
call gradlew.bat :smarttubetv:assembleStstableRelease --build-cache --parallel
call gradlew.bat --stop
