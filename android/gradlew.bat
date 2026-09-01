@rem Jarvis Android Gradle Wrapper Batch Script for Windows
@echo off
set DIR=%~dp0

where java >nul 2>nul
if %ERRORLEVEL% neq 0 (
    if "%JAVA_HOME%" == "" (
        echo ==================================================================================
        echo [JARVIS BUILD ERROR] Java JDK 17 is not installed or not found in PATH or JAVA_HOME.
        echo ==================================================================================
        echo Please install OpenJDK 17 or Eclipse Temurin 17 and set JAVA_HOME.
        echo ==================================================================================
        exit /b 1
    )
    set JAVACMD="%JAVA_HOME%\bin\java.exe"
) else (
    set JAVACMD=java
)

if "%ANDROID_HOME%" == "" (
    if exist "%LOCALAPPDATA%\Android\Sdk" (
        set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
    )
)

where gradle >nul 2>nul
if %ERRORLEVEL% equ 0 (
    gradle %*
) else if exist "%DIR%gradle\wrapper\gradle-wrapper.jar" (
    %JAVACMD% -jar "%DIR%gradle\wrapper\gradle-wrapper.jar" %*
) else (
    echo ==================================================================================
    echo [JARVIS BUILD NOTICE] System Gradle not detected. Please install Gradle for Windows.
    echo ==================================================================================
    exit /b 1
)
