@echo off
REM ============================================
REM JChatMind Test Runner - Windows
REM ============================================
echo Running JChatMind unit tests...
echo.

cd /d "%~dp0.."

if exist "mvnw.cmd" (
    call mvnw.cmd test
) else (
    mvn test
)

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ============================================
    echo  TESTS FAILED!
    echo ============================================
    exit /b 1
)

echo.
echo ============================================
echo  ALL TESTS PASSED!
echo ============================================
exit /b 0
