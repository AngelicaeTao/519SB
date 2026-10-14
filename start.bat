@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

title 519SB 聊天室 - 一键启动
cd /d "%~dp0"

echo ==========================================
echo   519SB 聊天室 - 一键启动
echo ==========================================
echo.

REM ===== 1. 检查 ngrok.exe =====
if not exist "ngrok.exe" (
    echo [错误] 找不到 ngrok.exe
    echo 请从 https://ngrok.com/download 下载并放到项目根目录
    pause
    exit /b 1
)

REM ===== 2. 检查 keystore.p12 =====
if not exist "keystore.p12" (
    echo [提示] 找不到 keystore.p12，运行 setup.bat...
    if not exist "setup.bat" (
        echo [错误] 找不到 setup.bat
        pause
        exit /b 1
    )
    call setup.bat
    if not exist "keystore.p12" (
        echo [错误] 证书生成失败
        pause
        exit /b 1
    )
)

REM ===== 3. 查找 Maven 本地仓库里的依赖 jar =====
set M2=%USERPROFILE%\.m2\repository

set JAR_SQLITE=%M2%\org\xerial\sqlite-jdbc\3.45.3.0\sqlite-jdbc-3.45.3.0.jar
set JAR_SLF4J_API=%M2%\org\slf4j\slf4j-api\2.0.12\slf4j-api-2.0.12.jar
set JAR_SLF4J_SIMPLE=%M2%\org\slf4j\slf4j-simple\2.0.12\slf4j-simple-2.0.12.jar

if not exist "!JAR_SQLITE!" (
    echo [错误] 找不到 sqlite-jdbc.jar
    echo 期望位置：!JAR_SQLITE!
    echo.
    echo 请先用 IDEA 打开项目，让 Maven 下载依赖
    pause
    exit /b 1
)
if not exist "!JAR_SLF4J_API!" (
    echo [错误] 找不到 slf4j-api.jar
    pause
    exit /b 1
)
if not exist "!JAR_SLF4J_SIMPLE!" (
    echo [错误] 找不到 slf4j-simple.jar
    pause
    exit /b 1
)

REM 页面模板放在 src\main\resources\web，运行时从 classpath 读取，所以要把它也加进来
set CP_DEPS=!JAR_SQLITE!;!JAR_SLF4J_API!;!JAR_SLF4J_SIMPLE!;src\main\resources

REM ===== 4. 编译 =====
echo [1/3] 编译 Java 代码...
if not exist "out" mkdir out

REM 收集所有 .java 文件
> "%TEMP%\519sb_sources.txt" (
    for /r "src\main\java" %%f in (*.java) do echo %%f
)

javac -encoding UTF-8 -d out -cp "!CP_DEPS!" @"%TEMP%\519sb_sources.txt"
if errorlevel 1 (
    echo [错误] 编译失败
    del "%TEMP%\519sb_sources.txt" >nul 2>&1
    pause
    exit /b 1
)
del "%TEMP%\519sb_sources.txt" >nul 2>&1

echo     编译完成
echo.

REM ===== 5. 启动 ngrok（亚太节点）=====
echo [2/3] 启动 ngrok（亚太节点）...
start "ngrok - 519SB" cmd /k "ngrok http https://localhost:8443 --region ap"

timeout /t 2 /nobreak >nul

REM ===== 6. 启动 ChatServer =====
echo [3/3] 启动 ChatServer...
start "ChatServer - 519SB" cmd /k "java -cp "out;!CP_DEPS!" BootstrapLayer.ChatServer"

echo.
echo ==========================================
echo   启动完成
echo ==========================================
echo   ngrok 窗口    : 查看 Forwarding 那一行的公网地址
echo   ChatServer窗口: 查看服务器日志
echo.
echo   关闭服务：关闭这两个窗口，或在窗口里按 Ctrl + C
echo ==========================================
echo.
pause
endlocal