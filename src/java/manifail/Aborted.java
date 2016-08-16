package manifail;

/**
 * Exception thrown when the execution is aborted.
 */
public class Aborted extends RuntimeException {
    private static final long serialVersionUID = -3120638907441644021L;
    public Aborted() {}
    public Aborted(Throwable cause) {
        super(cause);
    }
}
