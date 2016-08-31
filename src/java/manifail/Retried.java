package manifail;

/**
 * Exception thrown when the execution is retried.
 */
public class Retried extends Throwable {
    private static final long serialVersionUID = -5238638352410341922L;
    public Retried() {}
    public Retried(Throwable cause) {
        super(cause);
    }
}
