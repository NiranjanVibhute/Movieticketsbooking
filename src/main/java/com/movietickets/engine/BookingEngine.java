package com.movietickets.engine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BookingEngine {
    private static BookingEngine instance;

    // Split-Pay In-Memory Cache (10-minute lock expiry)
    private final Map<String, SplitPaySession> splitPayCache = new ConcurrentHashMap<>();
    private final Map<String, Long> seatLocks = new ConcurrentHashMap<>();

    private BookingEngine() {
        // Background daemon thread to automatically clean up expired locks and split-pay sessions
        Thread cleanerThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(15000); // Check every 15s
                    long now = System.currentTimeMillis();
                    splitPayCache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
                    seatLocks.entrySet().removeIf(entry -> entry.getValue() < now);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        cleanerThread.setDaemon(true);
        cleanerThread.start();
    }

    public static synchronized BookingEngine getInstance() {
        if (instance == null) {
            instance = new BookingEngine();
        }
        return instance;
    }

    // ==========================================
    // 1. GAP MINIMIZER OPTIMIZATION ALGORITHM
    // ==========================================
    public static class SeatData {
        public String id;
        public String row;
        public int number;
        public String tier;
        public double price;
        public boolean isBooked;
        public String vibeTag;

        public SeatData(String id, String row, int number, String tier, double price, boolean isBooked, String vibeTag) {
            this.id = id;
            this.row = row;
            this.number = number;
            this.tier = tier;
            this.price = price;
            this.isBooked = isBooked;
            this.vibeTag = vibeTag;
        }
    }

    public static class OptimizationResult {
        public List<SeatData> recommendedSeats;
        public String row;
        public int score;
        public String reason;
        public boolean found;

        public OptimizationResult(List<SeatData> seats, String row, int score, String reason, boolean found) {
            this.recommendedSeats = seats;
            this.row = row;
            this.score = score;
            this.reason = reason;
            this.found = found;
        }
    }

    /**
     * Gap Minimizer: Finds contiguous N seats that do not leave isolated single-seat gaps (orphan seats)
     * and prioritizes central view rows (C, D, E) and vibe alignment.
     */
    public OptimizationResult findOptimalSeats(List<SeatData> allSeats, int groupSize, String preferredVibe, String preferredTier) {
        if (groupSize <= 0 || allSeats == null || allSeats.isEmpty()) {
            return new OptimizationResult(Collections.emptyList(), "", 0, "Invalid group size or seating map", false);
        }

        // Group seats by row
        Map<String, List<SeatData>> rowMap = allSeats.stream()
                .sorted(Comparator.comparingInt(s -> s.number))
                .collect(Collectors.groupingBy(s -> s.row, TreeMap::new, Collectors.toList()));

        OptimizationResult bestResult = null;
        int highestScore = Integer.MIN_VALUE;

        // Rows in preference order (VIP / Central optimal rows)
        List<String> preferredRowOrder = Arrays.asList("D", "C", "E", "B", "F", "A", "G");

        for (String rowKey : preferredRowOrder) {
            List<SeatData> rowSeats = rowMap.get(rowKey);
            if (rowSeats == null || rowSeats.size() < groupSize) continue;

            int n = rowSeats.size();
            for (int i = 0; i <= n - groupSize; i++) {
                // Check if all seats from i to i + groupSize - 1 are available and not locked
                boolean allFree = true;
                List<SeatData> candidate = new ArrayList<>();
                for (int j = i; j < i + groupSize; j++) {
                    SeatData s = rowSeats.get(j);
                    if (s.isBooked || isSeatLocked(s.id)) {
                        allFree = false;
                        break;
                    }
                    candidate.add(s);
                }

                if (!allFree) continue;

                // Evaluate Gap Penalty / Quality Score
                int score = 100;

                // 1. Center of row bonus
                double centerIndex = (n - 1) / 2.0;
                double candidateCenter = (i + (i + groupSize - 1)) / 2.0;
                double distFromCenter = Math.abs(centerIndex - candidateCenter);
                score -= (int) (distFromCenter * 8);

                // 2. Row depth weight
                if (rowKey.equals("D") || rowKey.equals("C")) score += 30;
                else if (rowKey.equals("E") || rowKey.equals("B")) score += 20;

                // 3. Gap penalty: Check left side
                if (i > 0) {
                    // Check gap size to the left boundary or nearest booked seat
                    int leftGap = 0;
                    for (int k = i - 1; k >= 0; k--) {
                        if (!rowSeats.get(k).isBooked && !isSeatLocked(rowSeats.get(k).id)) {
                            leftGap++;
                        } else {
                            break;
                        }
                    }
                    if (leftGap == 1) {
                        score -= 50; // Severe penalty for single isolated orphan gap!
                    } else if (leftGap == 0) {
                        score += 15; // Flushes nicely against an occupied seat/wall
                    }
                }

                // 4. Gap penalty: Check right side
                int rightStart = i + groupSize;
                if (rightStart < n) {
                    int rightGap = 0;
                    for (int k = rightStart; k < n; k++) {
                        if (!rowSeats.get(k).isBooked && !isSeatLocked(rowSeats.get(k).id)) {
                            rightGap++;
                        } else {
                            break;
                        }
                    }
                    if (rightGap == 1) {
                        score -= 50; // Severe penalty for single isolated orphan gap!
                    } else if (rightGap == 0) {
                        score += 15; // Flushes cleanly
                    }
                }

                // 5. Preferred Vibe matching bonus
                if (preferredVibe != null && !preferredVibe.equalsIgnoreCase("NONE")) {
                    long matchingVibeCount = candidate.stream()
                            .filter(s -> s.vibeTag.equalsIgnoreCase(preferredVibe))
                            .count();
                    score += (int) (matchingVibeCount * 12);
                }

                // 6. Tier preference
                if (preferredTier != null && !preferredTier.isEmpty()) {
                    if (candidate.get(0).tier.equalsIgnoreCase(preferredTier)) {
                        score += 25;
                    }
                }

                if (score > highestScore) {
                    highestScore = score;
                    String explanation = String.format("Row %s: Optimal contiguous block %s%d-%s%d (No 1-seat gaps left, central audio focus)",
                            rowKey, rowKey, candidate.get(0).number, rowKey, candidate.get(candidate.size() - 1).number);
                    bestResult = new OptimizationResult(candidate, rowKey, score, explanation, true);
                }
            }
        }

        if (bestResult != null) {
            return bestResult;
        }

        return new OptimizationResult(Collections.emptyList(), "", 0, "No contiguous block found without fragmentation", false);
    }

    // ==========================================
    // 2. VIBE HEATMAP ENGINE
    // ==========================================
    public static class VibeHeatmap {
        public Map<String, Double> vibeDistribution = new HashMap<>();
        public Map<String, Integer> zoneCounts = new HashMap<>();
        public Map<String, String> seatVibeMap = new HashMap<>();
        public List<String> quietRecommendedRows = new ArrayList<>();
        public List<String> excitedFanRecommendedRows = new ArrayList<>();
        public List<String> familyRecommendedRows = new ArrayList<>();
        public double totalCapacity;
        public double bookedRatio;
    }

    public VibeHeatmap generateVibeHeatmap(List<SeatData> seats) {
        VibeHeatmap heatmap = new VibeHeatmap();
        if (seats == null || seats.isEmpty()) return heatmap;

        int total = seats.size();
        int booked = 0;
        int quietCount = 0;
        int fanCount = 0;
        int familyCount = 0;

        Map<String, Map<String, Integer>> rowVibeStats = new HashMap<>();

        for (SeatData s : seats) {
            if (s.isBooked) booked++;
            String vibe = s.vibeTag != null ? s.vibeTag.toUpperCase() : "NONE";
            heatmap.seatVibeMap.put(s.id, vibe);

            rowVibeStats.putIfAbsent(s.row, new HashMap<>());
            Map<String, Integer> rowMap = rowVibeStats.get(s.row);
            rowMap.put(vibe, rowMap.getOrDefault(vibe, 0) + 1);

            switch (vibe) {
                case "QUIET":
                    quietCount++;
                    break;
                case "EXCITED_FAN":
                    fanCount++;
                    break;
                case "FAMILY":
                    familyCount++;
                    break;
            }
        }

        heatmap.totalCapacity = total;
        heatmap.bookedRatio = Math.round(((double) booked / total) * 100.0) / 100.0;

        heatmap.zoneCounts.put("QUIET", quietCount);
        heatmap.zoneCounts.put("EXCITED_FAN", fanCount);
        heatmap.zoneCounts.put("FAMILY", familyCount);

        heatmap.vibeDistribution.put("QUIET", Math.round(((double) quietCount / total) * 1000.0) / 10.0);
        heatmap.vibeDistribution.put("EXCITED_FAN", Math.round(((double) fanCount / total) * 1000.0) / 10.0);
        heatmap.vibeDistribution.put("FAMILY", Math.round(((double) familyCount / total) * 1000.0) / 10.0);

        // Row recommendations based on dominant vibe
        for (Map.Entry<String, Map<String, Integer>> entry : rowVibeStats.entrySet()) {
            String row = entry.getKey();
            Map<String, Integer> counts = entry.getValue();
            int q = counts.getOrDefault("QUIET", 0);
            int fan = counts.getOrDefault("EXCITED_FAN", 0);
            int fam = counts.getOrDefault("FAMILY", 0);

            if (q >= fan && q >= fam && q > 0) heatmap.quietRecommendedRows.add("Row " + row);
            if (fan >= q && fan >= fam && fan > 0) heatmap.excitedFanRecommendedRows.add("Row " + row);
            if (fam >= q && fam >= fan && fam > 0) heatmap.familyRecommendedRows.add("Row " + row);
        }

        return heatmap;
    }

    // ==========================================
    // 3. SPLIT-PAY 10-MINUTE LOCK CACHE
    // ==========================================
    public static class SplitPaySession {
        public String splitCode;
        public String movieId;
        public String showTime;
        public List<String> seatIds;
        public double totalAmount;
        public double perPersonAmount;
        public int totalParticipants;
        public List<String> paidParticipants = new ArrayList<>();
        public long createdAt;
        public long expiresAt;
        public boolean isCompleted = false;

        public SplitPaySession(String splitCode, String movieId, String showTime, List<String> seatIds, double totalAmount, int participants) {
            this.splitCode = splitCode;
            this.movieId = movieId;
            this.showTime = showTime;
            this.seatIds = seatIds;
            this.totalAmount = totalAmount;
            this.totalParticipants = Math.max(1, participants);
            this.perPersonAmount = Math.round((totalAmount / this.totalParticipants) * 100.0) / 100.0;
            this.createdAt = System.currentTimeMillis();
            this.expiresAt = this.createdAt + (10 * 60 * 1000); // 10 minutes TTL
        }

        public boolean isExpired(long now) {
            return now > expiresAt;
        }

        public long getRemainingSeconds() {
            long remaining = (expiresAt - System.currentTimeMillis()) / 1000;
            return Math.max(0, remaining);
        }
    }

    public SplitPaySession createSplitSession(String movieId, String showTime, List<String> seatIds, double totalAmount, int participants, String creatorName) {
        String splitCode = "SPLIT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        SplitPaySession session = new SplitPaySession(splitCode, movieId, showTime, seatIds, totalAmount, participants);
        
        // Creator auto-contributes or sets first slot
        if (creatorName != null && !creatorName.isEmpty()) {
            session.paidParticipants.add(creatorName + " (Organizer)");
        }

        // Lock seats for 10 minutes
        long lockUntil = session.expiresAt;
        for (String seatId : seatIds) {
            seatLocks.put(seatId, lockUntil);
        }

        splitPayCache.put(splitCode, session);
        return session;
    }

    public SplitPaySession getSplitSession(String splitCode) {
        SplitPaySession session = splitPayCache.get(splitCode);
        if (session != null && session.isExpired(System.currentTimeMillis())) {
            splitPayCache.remove(splitCode);
            // Release locks
            for (String seatId : session.seatIds) {
                seatLocks.remove(seatId);
            }
            return null;
        }
        return session;
    }

    public boolean contributeToSplit(String splitCode, String participantName) {
        SplitPaySession session = getSplitSession(splitCode);
        if (session == null || session.isCompleted) return false;

        session.paidParticipants.add(participantName);
        if (session.paidParticipants.size() >= session.totalParticipants) {
            session.isCompleted = true;
        }
        return true;
    }

    public void lockSeat(String seatId, int durationMinutes) {
        seatLocks.put(seatId, System.currentTimeMillis() + (durationMinutes * 60L * 1000L));
    }

    public boolean isSeatLocked(String seatId) {
        Long expireTime = seatLocks.get(seatId);
        if (expireTime == null) return false;
        if (System.currentTimeMillis() > expireTime) {
            seatLocks.remove(seatId);
            return false;
        }
        return true;
    }

    public void unlockSeats(List<String> seatIds) {
        if (seatIds != null) {
            seatIds.forEach(seatLocks::remove);
        }
    }

    // ==========================================
    // 4. SNACK BUILDER (JAVA DECORATOR PATTERN)
    // ==========================================
    public interface SnackItem {
        String getDescription();
        double getPrice();
        int getCalories();
        String getBaseCategory();
    }

    // Concrete Base Snack Components
    public static class BasePopcorn implements SnackItem {
        @Override
        public String getDescription() { return "Cyber Truffle Popcorn"; }
        @Override
        public double getPrice() { return 7.50; }
        @Override
        public int getCalories() { return 420; }
        @Override
        public String getBaseCategory() { return "POPCORN"; }
    }

    public static class BaseNachos implements SnackItem {
        @Override
        public String getDescription() { return "Volcanic Loaded Nachos"; }
        @Override
        public double getPrice() { return 9.00; }
        @Override
        public int getCalories() { return 680; }
        @Override
        public String getBaseCategory() { return "NACHOS"; }
    }

    public static class BaseBeverage implements SnackItem {
        @Override
        public String getDescription() { return "Quantum Neon Slush / Soda"; }
        @Override
        public double getPrice() { return 5.00; }
        @Override
        public int getCalories() { return 180; }
        @Override
        public String getBaseCategory() { return "DRINKS"; }
    }

    public static class BaseHotdog implements SnackItem {
        @Override
        public String getDescription() { return "Gourmet Angus Beef Dog"; }
        @Override
        public double getPrice() { return 8.50; }
        @Override
        public int getCalories() { return 520; }
        @Override
        public String getBaseCategory() { return "COMBOS"; }
    }

    // Abstract Decorator
    public static abstract class SnackDecorator implements SnackItem {
        protected SnackItem decoratedSnack;

        public SnackDecorator(SnackItem snack) {
            this.decoratedSnack = snack;
        }

        @Override
        public String getDescription() { return decoratedSnack.getDescription(); }
        @Override
        public double getPrice() { return decoratedSnack.getPrice(); }
        @Override
        public int getCalories() { return decoratedSnack.getCalories(); }
        @Override
        public String getBaseCategory() { return decoratedSnack.getBaseCategory(); }
    }

    // Concrete Decorators
    public static class ExtraCheeseDecorator extends SnackDecorator {
        public ExtraCheeseDecorator(SnackItem snack) { super(snack); }
        @Override
        public String getDescription() { return decoratedSnack.getDescription() + " + Extra Queso Cheese Drizzle"; }
        @Override
        public double getPrice() { return Math.round((decoratedSnack.getPrice() + 1.75) * 100.0) / 100.0; }
        @Override
        public int getCalories() { return decoratedSnack.getCalories() + 140; }
    }

    public static class TruffleButterDecorator extends SnackDecorator {
        public TruffleButterDecorator(SnackItem snack) { super(snack); }
        @Override
        public String getDescription() { return decoratedSnack.getDescription() + " + White Truffle Glaze"; }
        @Override
        public double getPrice() { return Math.round((decoratedSnack.getPrice() + 2.50) * 100.0) / 100.0; }
        @Override
        public int getCalories() { return decoratedSnack.getCalories() + 110; }
    }

    public static class LargeSizeDecorator extends SnackDecorator {
        public LargeSizeDecorator(SnackItem snack) { super(snack); }
        @Override
        public String getDescription() { return "Mega-Size [ " + decoratedSnack.getDescription() + " ]"; }
        @Override
        public double getPrice() { return Math.round((decoratedSnack.getPrice() + 2.25) * 100.0) / 100.0; }
        @Override
        public int getCalories() { return (int) Math.round(decoratedSnack.getCalories() * 1.5); }
    }

    public static class CaramelGlazeDecorator extends SnackDecorator {
        public CaramelGlazeDecorator(SnackItem snack) { super(snack); }
        @Override
        public String getDescription() { return decoratedSnack.getDescription() + " + Artisan Salted Caramel"; }
        @Override
        public double getPrice() { return Math.round((decoratedSnack.getPrice() + 1.80) * 100.0) / 100.0; }
        @Override
        public int getCalories() { return decoratedSnack.getCalories() + 160; }
    }

    public static class JalapenoDecorator extends SnackDecorator {
        public JalapenoDecorator(SnackItem snack) { super(snack); }
        @Override
        public String getDescription() { return decoratedSnack.getDescription() + " + Fire Roasted Jalapeños"; }
        @Override
        public double getPrice() { return Math.round((decoratedSnack.getPrice() + 1.25) * 100.0) / 100.0; }
        @Override
        public int getCalories() { return decoratedSnack.getCalories() + 25; }
    }

    public static class ComboMealDecorator extends SnackDecorator {
        public ComboMealDecorator(SnackItem snack) { super(snack); }
        @Override
        public String getDescription() { return decoratedSnack.getDescription() + " + Cyber Combo (Large Soda & Candy Box Included)"; }
        @Override
        public double getPrice() { return Math.round((decoratedSnack.getPrice() + 4.50) * 100.0) / 100.0; }
        @Override
        public int getCalories() { return decoratedSnack.getCalories() + 320; }
    }

    /**
     * Fluent Builder helper to construct decorated snack item and calculate fluid pricing & calories
     */
    public SnackItem buildSnack(String baseType, List<String> addOns) {
        SnackItem item;
        switch (baseType.toUpperCase()) {
            case "NACHOS":
                item = new BaseNachos();
                break;
            case "DRINKS":
            case "BEVERAGE":
                item = new BaseBeverage();
                break;
            case "HOTDOG":
            case "COMBOS":
                item = new BaseHotdog();
                break;
            case "POPCORN":
            default:
                item = new BasePopcorn();
                break;
        }

        if (addOns != null) {
            for (String addOn : addOns) {
                switch (addOn.toUpperCase()) {
                    case "CHEESE":
                    case "EXTRA_CHEESE":
                        item = new ExtraCheeseDecorator(item);
                        break;
                    case "TRUFFLE":
                    case "TRUFFLE_BUTTER":
                        item = new TruffleButterDecorator(item);
                        break;
                    case "LARGE":
                    case "MEGA_SIZE":
                        item = new LargeSizeDecorator(item);
                        break;
                    case "CARAMEL":
                        item = new CaramelGlazeDecorator(item);
                        break;
                    case "JALAPENO":
                        item = new JalapenoDecorator(item);
                        break;
                    case "COMBO":
                    case "COMBO_UPGRADE":
                        item = new ComboMealDecorator(item);
                        break;
                }
            }
        }
        return item;
    }
}
