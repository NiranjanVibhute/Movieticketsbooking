# Dark-Ops Cinema PowerShell Launch Script
Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host "  Dark-Ops Cinema - Movie Ticket Booking App" -ForegroundColor Yellow
Write-Host "=======================================================" -ForegroundColor Cyan

if (-not (Test-Path "lib")) { New-Item -ItemType Directory -Path "lib" | Out-Null }
if (-not (Test-Path "lib\sqlite-jdbc.jar")) {
    Write-Host "[Setup] Downloading SQLite JDBC Driver..." -ForegroundColor Magenta
    Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.1.0/sqlite-jdbc-3.45.1.0.jar" -OutFile "lib\sqlite-jdbc.jar"
}
if (-not (Test-Path "lib\slf4j-api.jar")) {
    Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.12/slf4j-api-2.0.12.jar" -OutFile "lib\slf4j-api.jar"
}
if (-not (Test-Path "lib\slf4j-simple.jar")) {
    Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.12/slf4j-simple-2.0.12.jar" -OutFile "lib\slf4j-simple.jar"
}

if (-not (Test-Path "bin")) { New-Item -ItemType Directory -Path "bin" | Out-Null }

Write-Host "[Build] Compiling Java source files..." -ForegroundColor Green
javac -encoding UTF-8 -cp "lib/*;src/main/resources" -d bin src/main/java/com/movietickets/*.java src/main/java/com/movietickets/db/*.java src/main/java/com/movietickets/engine/*.java src/main/java/com/movietickets/controller/*.java

if ($LASTEXITCODE -ne 0) {
    Write-Host "[Error] Java compilation failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

if (Test-Path "src\main\resources\schema.sql") {
    Copy-Item "src\main\resources\schema.sql" -Destination "bin\schema.sql" -Force
}

Write-Host "[Launch] Starting AppServer at http://localhost:8080 ..." -ForegroundColor Cyan
java -cp "bin;lib/*" com.movietickets.AppServer
