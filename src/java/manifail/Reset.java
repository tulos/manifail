package manifail;

/**
 * Exception thrown when the execution is reset.
 */
public class Reset extends Throwable {
    private static final long serialVersionUID = -1234543212343211234L;
    public final Object retryDelays;
    public Reset(Object retryDelays) {
        this.retryDelays = retryDelays;
    }
}
