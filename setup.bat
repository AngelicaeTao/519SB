@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ==========================================
echo   519SB Chat Room - Setup Script
echo ==========================================
echo.

REM ---- Check keytool ----
where keytool >nul 2>&1
if errorlevel 1 (
    echo [ERROR] keytool not found.
    echo Please install JDK 17+ and make sure its bin folder is in PATH.
    echo Download JDK: https://adoptium.net/
    pause
    exit /b 1
)

REM ---- Create data folder ----
if not exist "data" mkdir data

REM ---- Check existing keystore ----
if exist "keystore.p12" (
    echo Found existing keystore.p12. Deleting...
    del keystore.p12
)

echo Generating certificate...
keytool -genkeypair -alias myserver -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.p12 -storepass changeit -validity 365 -dname "CN=519SB-chat-room" -ext "SAN=IP:127.0.0.1,DNS:localhost"

if errorlevel 1 (
    echo.
    echo [ERROR] Certificate generation failed.
    pause
    exit /b 1
)

echo.
echo ==========================================
echo   [OK] keystore.p12 generated
echo ==========================================
echo.
echo Next steps:
echo   1. Run ChatServer in IDEA
echo   2. In another terminal: ngrok http https://localhost:8443
echo   3. Share the ngrok URL with your friends
echo.
pause