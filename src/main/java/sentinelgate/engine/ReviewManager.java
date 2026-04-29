package sentinelgate.engine;

import sentinelgate.model.FraudFlag;

import java.util.HashMap;
import java.util.Map;

public class ReviewManager {

    public enum Disposition {
        PENDING,
        REVIEWED,
        DISMISSED
    }

    private Map<FraudFlag, Disposition> flagStates;

    public ReviewManager() {
        this.flagStates = new HashMap<>();
    }

    // Add a new flag as pending
    public void addFlag(FraudFlag flag) {
        flagStates.putIfAbsent(flag, Disposition.PENDING);
    }

    // Update the disposition
    public void markDisposition(FraudFlag flag, Disposition disposition) {
        if (flagStates.containsKey(flag)) {
            flagStates.put(flag, disposition);
        }
    }

    public Disposition getDisposition(FraudFlag flag) {
        return flagStates.getOrDefault(flag, Disposition.PENDING);
    }

    public Map<FraudFlag, Disposition> getAllFlags() {
        return flagStates;
    }
}
