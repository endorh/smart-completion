@echo off

set gradleCommand=%*

call :GetProperty mcVersions
set mcVersions=%mcVersions: =%
call :GetProperty modId
call :GetProperty modName
call :GetProperty modVersion

echo Building %modName% (%modId%) v%modVersion% for Minecraft versions:
echo   %mcVersions:,=, %
echo.

for %%a in ("%mcVersions:,=" "%") do (
   call :RunForVersion %%~a "%gradleCommand%"
)
exit /b %ERRORLEVEL%

:GetProperty
for /f "usebackq tokens=*" %%a in (`findstr "%~1" "gradle.properties"`) do set prop=%%a
for /f "tokens=2 delims==" %%a in ("%prop%") do set prop=%%a
for /f "tokens=* delims= " %%a in ("%prop%") do set prop=%%a
set "%~1=%prop%"
exit /b 0

:RunForVersion
echo Building for Minecraft %~1
@echo on
call ./gradlew.bat %~2 -PmcVersion="%~1"
@echo off
echo.
exit /b 0

