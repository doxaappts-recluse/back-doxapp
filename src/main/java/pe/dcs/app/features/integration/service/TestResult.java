package pe.dcs.app.features.integration.service;

public record TestResult(boolean ok, String error) {
    public static TestResult success() {
        return new TestResult(true, null);
    }

    public static TestResult fail(String error) {
        return new TestResult(false, error);
    }
}
