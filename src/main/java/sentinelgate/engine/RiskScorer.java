package sentinelgate.engine;

import sentinelgate.model.FraudFlag;

import java.util.*;

public class RiskScorer {

    // Weights for each severity level
    private static final Map<String, Integer> SEVERITY_SCORES = Map.of(
            "HIGH",   3,
            "MEDIUM", 2,
            "LOW",    1
    );

    // Bonus points when multiple rules fire on the same transaction
    private static final int CO_OCCURRENCE_BONUS = 2;

    // Max possible score = all HIGH rules + co-occurrence bonus (used for display)
    public static final int MAX_SCORE = 9;

    /**
     * Scores a single flag in isolation.
     */
    public int scoreFlag(FraudFlag flag) {
        return SEVERITY_SCORES.getOrDefault(flag.getSeverity(), 1);
    }

    /**
     * Scores a flag in the context of a full list of flags triggered by the same transaction.
     * Co-occurrence bonus is applied when more than one rule fired.
     */
    public int scoreInContext(FraudFlag flag, List<FraudFlag> allFlagsForTransaction) {
        int base = scoreFlag(flag);
        int siblingCount = allFlagsForTransaction.size() - 1;

        if (siblingCount > 0) {
            base += (siblingCount * CO_OCCURRENCE_BONUS);
        }
        return Math.min(base, MAX_SCORE);
    }

    /**
     * Takes a map of flag -> transaction's full flag list, and returns the flags
     * sorted from highest risk score to lowest.
     */
    public List<Map.Entry<FraudFlag, Integer>> rankFlags(Map<FraudFlag, List<FraudFlag>> flagContextMap) {
        List<Map.Entry<FraudFlag, Integer>> scored = new ArrayList<>();

        for (Map.Entry<FraudFlag, List<FraudFlag>> entry : flagContextMap.entrySet()) {
            int score = scoreInContext(entry.getKey(), entry.getValue());
            scored.add(Map.entry(entry.getKey(), score));
        }

        // Sort descending by score
        scored.sort((a, b) -> b.getValue() - a.getValue());
        return scored;
    }
}