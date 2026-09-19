@echo off
setlocal

echo =======================================================
echo   Dark-Ops Cinema - Movie Ticket Booking App
echo =======================================================

if not exist "lib" mkdir lib
if not exist "lib\sqlite-jdbc.jar" (
    echo [Setup] Downloading SQLite JDBC Driver...
    powershell -Command "Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.1.0/sqlite-jdbc-3.45.1.0.jar' -OutFile 'lib\sqlite-jdbc.jar'"
)
if not exist "lib\slf4j-api.jar" (
    powershell -Command "Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.12/slf4j-api-2.0.12.jar' -OutFile 'lib\slf4j-api.jar'"
)
if not exist "lib\slf4j-simple.jar" (
    powershell -Command "Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.12/slf4j-simple-2.0.12.jar' -OutFile 'lib\slf4j-simple.jar'"
)

if not exist "bin" mkdir bin

echo [Build] Compiling Java classes...
javac -encoding UTF-8 -cp "lib\*;src\main\resources" -d bin src\main\java\com\movietickets\*.java src\main\java\com\movietickets\db\*.java src\main\java\com\movietickets\engine\*.java src\main\java\com\movietickets\controller\*.java

if %ERRORLEVEL% NEQ 0 (
    echo [Error] Compilation failed!
    pause
    exit /b %ERRORLEVEL%
)

echo [Copy] Copying resource files...
if exist "src\main\resources\schema.sql" copy /Y "src\main\resources\schema.sql" "bin\" >nul

echo [Launch] Starting Dark-Ops Movie Booking Server on http://localhost:8080 ...
java -cp "bin;lib\*" com.movietickets.AppServer

pause
