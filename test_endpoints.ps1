# Test all Dark-Ops endpoints
Write-Host "--- 1. Testing GET /api/movies ---" -ForegroundColor Cyan
$movies = Invoke-RestMethod -Uri "http://localhost:8080/api/movies"
Write-Host "Movies fetched:" $movies.Count
$blind = $movies | Where-Object { $_.isBlindBox -eq $true }
Write-Host "Blind Box Title:" $blind.title "| Discount:" $blind.blindBoxDiscountPct "%"

Write-Host "`n--- 2. Testing GET /api/seats/heatmap ---" -ForegroundColor Cyan
$heatmap = Invoke-RestMethod -Uri "http://localhost:8080/api/seats/heatmap?movieId=mov-dune2&showTime=18:30"
Write-Host "Vibe Stats:" ($heatmap.vibeDistribution | ConvertTo-Json -Compress)

Write-Host "`n--- 3. Testing POST /api/seats/optimize (Gap Minimizer) ---" -ForegroundColor Cyan
$optBody = @{ movieId = "mov-dune2"; showTime = "18:30"; groupSize = 2; preferredVibe = "QUIET" } | ConvertTo-Json
$opt = Invoke-RestMethod -Uri "http://localhost:8080/api/seats/optimize" -Method Post -Body $optBody -ContentType "application/json"
Write-Host "Optimization:" $opt.row "| Score:" $opt.score "| Reason:" $opt.reason

Write-Host "`n--- 4. Testing POST /api/snacks/calculate (Java Decorator Pattern) ---" -ForegroundColor Cyan
$snackBody = @{ baseType = "POPCORN"; addOns = @("CHEESE", "LARGE", "TRUFFLE") } | ConvertTo-Json
$snack = Invoke-RestMethod -Uri "http://localhost:8080/api/snacks/calculate" -Method Post -Body $snackBody -ContentType "application/json"
Write-Host "Decorated Description:" $snack.description
Write-Host "Price: $" $snack.price "| Calories:" $snack.calories "kcal"

Write-Host "`n--- 5. Testing POST /api/split-pay/create (10-min Cache Lock) ---" -ForegroundColor Cyan
$splitBody = @{ movieId = "mov-dune2"; showTime = "18:30"; seatIds = @("mov-dune2_18:30_D4", "mov-dune2_18:30_D5"); totalAmount = 35.00; participants = 2; creatorName = "Alex" } | ConvertTo-Json
$split = Invoke-RestMethod -Uri "http://localhost:8080/api/split-pay/create" -Method Post -Body $splitBody -ContentType "application/json"
Write-Host "Split Code:" $split.splitCode "| Share: $" $split.perPersonAmount "| Remaining Sec:" $split.remainingSeconds

Write-Host "`n--- 6. Testing POST /api/bookings/checkout ---" -ForegroundColor Cyan
$checkoutBody = @{ movieId = "mov-blindbox"; showTime = "18:30"; seatIds = @("mov-blindbox_18:30_B3", "mov-blindbox_18:30_B4"); totalAmount = 23.98; vibePreference = "QUIET"; customerName = "Niranjan V."; customerEmail = "niranjan@darkops.cinema" } | ConvertTo-Json
$checkout = Invoke-RestMethod -Uri "http://localhost:8080/api/bookings/checkout" -Method Post -Body $checkoutBody -ContentType "application/json"
Write-Host "Booking Ref:" $checkout.bookingRef "| Revealed Movie:" $checkout.revealedMovieTitle "| Barcode:" $checkout.barcodeData

Write-Host "`n--- 7. Testing GET /api/bookings/{ref} ---" -ForegroundColor Cyan
$booking = Invoke-RestMethod -Uri "http://localhost:8080/api/bookings/$($checkout.bookingRef)"
Write-Host "Persisted Booking in SQLite:" $booking.movieTitle "| Seats:" $booking.seatNumbers "| Delivery Status:" $booking.deliveryStatus
