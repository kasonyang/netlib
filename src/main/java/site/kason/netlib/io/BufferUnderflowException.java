package site.kason.netlib.io;

/**
 * @author Kason Yang
 */
public class BufferUnderflowException extends RuntimeException {

    private final int requiredSize;

    private final int availableSize;

    public BufferUnderflowException(int requiredSize, int availableSize) {
        super(String.format("%d bytes required,but %d bytes available", requiredSize, availableSize));
        this.requiredSize = requiredSize;
        this.availableSize = availableSize;
    }

}
