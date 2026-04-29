package sentinelgate.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sentinelgate.model.FraudFlag;

import static org.junit.jupiter.api.Assertions.*;

class ReviewManagerTest {

    private ReviewManager manager;
    private FraudFlag testFlag1;
    private FraudFlag testFlag2;

    @BeforeEach
    void setUp() {
        manager = new ReviewManager();
        testFlag1 = new FraudFlag("Large Amount Rule", "HIGH", "Context 1");
        testFlag2 = new FraudFlag("High Frequency Rule", "LOW", "Context 2");
    }

    @Test
    void testAddFlag_InitializesToPending() {
        // Act
        manager.addFlag(testFlag1);

        // Assert
        assertEquals(ReviewManager.Disposition.PENDING, manager.getDisposition(testFlag1),
                "Newly added flags must default to PENDING.");
    }

    @Test
    void testMarkDisposition_UpdatesCorrectly() {
        // Arrange
        manager.addFlag(testFlag1);

        // Act
        manager.markDisposition(testFlag1, ReviewManager.Disposition.REVIEWED);

        // Assert
        assertEquals(ReviewManager.Disposition.REVIEWED, manager.getDisposition(testFlag1),
                "Flag disposition should update from PENDING to REVIEWED.");
    }

    @Test
    void testMultipleFlags_MaintainIndependentStates() {
        // Arrange
        manager.addFlag(testFlag1);
        manager.addFlag(testFlag2);

        // Act
        manager.markDisposition(testFlag1, ReviewManager.Disposition.REVIEWED);
        manager.markDisposition(testFlag2, ReviewManager.Disposition.DISMISSED);

        // Assert
        assertEquals(ReviewManager.Disposition.REVIEWED, manager.getDisposition(testFlag1),
                "Flag 1 should be REVIEWED.");
        assertEquals(ReviewManager.Disposition.DISMISSED, manager.getDisposition(testFlag2),
                "Flag 2 should be DISMISSED.");
        assertEquals(2, manager.getAllFlags().size(),
                "Manager should contain exactly 2 flags.");
    }

    @Test
    void testMarkDisposition_IgnoresUnknownFlagsSafely() {
        // Arrange: We intentionally do not add testFlag2 to the manager

        // Act: Try to update a flag the manager doesn't know about
        manager.markDisposition(testFlag2, ReviewManager.Disposition.DISMISSED);

        // Assert
        assertFalse(manager.getAllFlags().containsKey(testFlag2),
                "Marking an unknown flag should not forcefully add it to the tracking map.");

        // Our getDisposition method uses .getOrDefault(), so it should safely return PENDING
        assertEquals(ReviewManager.Disposition.PENDING, manager.getDisposition(testFlag2),
                "Querying an unknown flag should safely return PENDING.");
    }
}