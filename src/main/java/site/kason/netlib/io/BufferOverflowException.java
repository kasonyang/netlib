package site.kason.netlib.io;

/**
 * @author Kason Yang
 */
public class BufferOverflowException extends RuntimeException {

    private final int requiredSize;

    private final int availableSize;

    public BufferOverflowException(int requiredSize, int availableSize) {
        super(String.format("%d bytes required,but %d bytes available", requiredSize, availableSize));
        this.requiredSize = requiredSize;
        this.availableSize = availableSize;
    }

}
