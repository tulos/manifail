package manifail;

/**
 * Exception thrown when the execution is retried.
 */
public class Retried extends Throwable {
    private static final long serialVersionUID = -5238638352410341922L;
    public final Object value;
    public Retried() {
        this.value = null;
    }
    public Retried(Object value) {
        this.value = value;
    }
    public Retried(Throwable cause) {
        super(cause);
        this.value = null;
    }
}
