package sentinelgate.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sentinelgate.model.FraudFlag;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RiskScorerTest {

    private RiskScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new RiskScorer();
    }

    // --- scoreFlag (isolation) ---

    @Test
    void testScoreFlag_HighSeverity_ReturnsThree() {
        // Arrange
        FraudFlag flag = new FraudFlag("Large Amount Anomaly", "HIGH", "Context");

        // Act
        int score = scorer.scoreFlag(flag);

        // Assert
        assertEquals(3, score, "HIGH severity flag should score 3 in isolation.");
    }

    @Test
    void testScoreFlag_MediumSeverity_ReturnsTwo() {
        // Arrange
        FraudFlag flag = new FraudFlag("Location Mismatch", "MEDIUM", "Context");

        // Act
        int score = scorer.scoreFlag(flag);

        // Assert
        assertEquals(2, score, "MEDIUM severity flag should score 2 in isolation.");
    }

    @Test
    void testScoreFlag_LowSeverity_ReturnsOne() {
        // Arrange
        FraudFlag flag = new FraudFlag("Some Rule", "LOW", "Context");

        // Act
        int score = scorer.scoreFlag(flag);

        // Assert
        assertEquals(1, score, "LOW severity flag should score 1 in isolation.");
    }

    @Test
    void testScoreFlag_UnknownSeverity_DefaultsToOne() {
        // Edge case: a rule with an unrecognised severity string should not crash
        FraudFlag flag = new FraudFlag("Unknown Rule", "CRITICAL", "Context");

        // Act
        int score = scorer.scoreFlag(flag);

        // Assert
        assertEquals(1, score, "Unknown severity should safely default to the minimum score of 1.");
    }

    // --- scoreInContext (co-occurrence bonus) ---

    @Test
    void testScoreInContext_SingleFlag_NoBonus() {
        // Arrange: only one flag fired on this transaction — no co-occurrence bonus
        FraudFlag flag = new FraudFlag("Large Amount Anomaly", "HIGH", "Context");
        List<FraudFlag> siblings = List.of(flag);

        // Act
        int score = scorer.scoreInContext(flag, siblings);

        // Assert
        assertEquals(3, score, "A lone HIGH flag should score 3 with no co-occurrence bonus.");
    }

    @Test
    void testScoreInContext_MultipleFlags_AddsCOOccurrenceBonus() {
        // Arrange: two rules fired on the same transaction — bonus should apply
        FraudFlag flag1 = new FraudFlag("Large Amount Anomaly", "HIGH", "Context 1");
        FraudFlag flag2 = new FraudFlag("Location Mismatch", "MEDIUM", "Context 2");
        List<FraudFlag> siblings = List.of(flag1, flag2);

        // Act
        int score = scorer.scoreInContext(flag1, siblings);

        // Assert
        assertEquals(5, score, "HIGH flag with a sibling should score 3 (base) + 2 (co-occurrence) = 5.");
    }

    @Test
    void testScoreInContext_ScoreIsCapedAtMaxScore() {
        // Arrange: multiple HIGH flags — raw sum would exceed the cap
        FraudFlag flag1 = new FraudFlag("Rule A", "HIGH", "Context 1");
        FraudFlag flag2 = new FraudFlag("Rule B", "HIGH", "Context 2");
        FraudFlag flag3 = new FraudFlag("Rule C", "HIGH", "Context 3");
        FraudFlag flag4 = new FraudFlag("Rule D", "HIGH", "Context 4");
        FraudFlag flag5 = new FraudFlag("Rule E", "HIGH", "Context 5");
        List<FraudFlag> siblings = List.of(flag1, flag2, flag3, flag4, flag5);

        // Act
        int score = scorer.scoreInContext(flag1, siblings);

        // Assert
        assertEquals(RiskScorer.MAX_SCORE, score,
                "Score should never exceed MAX_SCORE (" + RiskScorer.MAX_SCORE + "), even with bonus.");
    }

    // --- rankFlags ---

    @Test
    void testRankFlags_ReturnsFlagsInDescendingScoreOrder() {
        // Arrange: a LOW flag alone and a HIGH flag with a sibling — the HIGH pair should rank first
        FraudFlag lowFlag  = new FraudFlag("Low Rule",  "LOW",  "Context");
        FraudFlag highFlag = new FraudFlag("High Rule", "HIGH", "Context");
        FraudFlag sibling  = new FraudFlag("Sibling",   "MEDIUM", "Context");

        Map<FraudFlag, List<FraudFlag>> contextMap = Map.of(
                lowFlag,  List.of(lowFlag),
                highFlag, List.of(highFlag, sibling)
        );

        // Act
        List<Map.Entry<FraudFlag, Integer>> ranked = scorer.rankFlags(contextMap);

        // Assert
        assertEquals(2, ranked.size(), "All flags should appear in the ranked list.");
        assertEquals(highFlag, ranked.get(0).getKey(),
                "The HIGH flag with a co-occurrence sibling should rank first.");
        assertEquals(lowFlag, ranked.get(1).getKey(),
                "The lone LOW flag should rank last.");
        assertTrue(ranked.get(0).getValue() >= ranked.get(1).getValue(),
                "Scores should be in non-ascending order.");
    }

    @Test
    void testRankFlags_EmptyMap_ReturnsEmptyList() {
        // Edge case: no flags to rank should not throw
        List<Map.Entry<FraudFlag, Integer>> ranked = scorer.rankFlags(Map.of());

        assertNotNull(ranked, "Result should not be null for an empty input.");
        assertTrue(ranked.isEmpty(), "Result should be empty when no flags are provided.");
    }
}