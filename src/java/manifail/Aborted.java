package manifail;

/**
 * Exception thrown when the execution is aborted.
 */
public class Aborted extends RuntimeException {
    private static final long serialVersionUID = -3120638907441644020L;
    public Aborted() {}
    public Aborted(String message) {
        super(message);
    }
}
