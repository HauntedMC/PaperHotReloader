package nl.hauntedmc.paperhotreloader.managers;

/** A user-facing outcome of one lifecycle action. */
public record OperationResult(String target, boolean success, String message) {

    public static OperationResult ok(String target, String message) {
        return new OperationResult(target, true, message);
    }

    public static OperationResult fail(String target, String message) {
        return new OperationResult(target, false, message);
    }
}
