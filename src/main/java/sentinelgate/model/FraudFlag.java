package sentinelgate.model;

public class FraudFlag {
    private String ruleName;
    private String severity; // "LOW", "MEDIUM", "HIGH"
    private String rawContext; // Raw data string that will be fed to AI later

    public FraudFlag(String ruleName, String severity, String rawContext) {
        this.ruleName = ruleName;
        this.severity = severity;
        this.rawContext = rawContext;
    }

    // Getters
    public String getRuleName() { return ruleName; }
    public String getSeverity() { return severity; }
    public String getRawContext() { return rawContext; }

    @Override
    public String toString() {
        return String.format("[%s] %s - %s", severity, ruleName, rawContext);
    }
}
