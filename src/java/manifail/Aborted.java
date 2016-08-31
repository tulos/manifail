package manifail;

/**
 * Exception thrown when the execution is aborted.
 */
public class Aborted extends Throwable {
    private static final long serialVersionUID = -3120638907441644021L;
    public final Object value;
    public Aborted() {
        this.value = null;
    }
    public Aborted(Object value) {
        this.value = value;
    }
    public Aborted(Throwable cause) {
        super(cause);
        this.value = null;
    }
}
