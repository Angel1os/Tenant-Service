package ecdc.tenant.service.enums;

public enum StatusCode {
    SUCCESS(0),
    CLIENT_ERROR(1),
    SERVER_ERROR(2);

    public final int value;
    private StatusCode(int value) {
        this.value = value;
    }
}
