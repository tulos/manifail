package manifail;

/**
 * Exception thrown when the number of retries is exceeded.
 */
public class RetriesExceeded extends RuntimeException {
    private static final long serialVersionUID = 1552386065262456540L;
    public final int retries;
    public RetriesExceeded(int retries) {
        this.retries = retries;
    }
}
