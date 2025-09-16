package editor.model;

public enum LockStatus {
    ACQUIRE(true), RELEASE(false);

    private final boolean boolValue;

    LockStatus(boolean bool) {
        this.boolValue = bool;
    }

    public boolean toBoolean() {
        return this.boolValue;
    }
}
