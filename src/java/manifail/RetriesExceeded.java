package manifail;

/**
 * Exception thrown when the number of retries is exceeded.
 */
public class RetriesExceeded extends Throwable {
    private static final long serialVersionUID = 1552386065262456541L;
    public final int retries;
    public RetriesExceeded(int retries, Throwable cause) {
        super(cause);
        this.retries = retries;
    }
}
