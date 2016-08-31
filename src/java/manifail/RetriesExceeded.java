package manifail;

/**
 * Exception thrown when the number of retries is exceeded.
 */
public class RetriesExceeded extends Throwable {
    private static final long serialVersionUID = 1552386065262456541L;
    public final int retries;
    public final Object value;
    public RetriesExceeded(int retries) {
        this.retries = retries;
        this.value = null;
    }
    public RetriesExceeded(int retries, Object value) {
        this.retries = retries;
        this.value = value;
    }
}
