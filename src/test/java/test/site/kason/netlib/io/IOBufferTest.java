package test.site.kason.netlib.io;

import org.junit.Test;
import static org.junit.Assert.*;
import site.kason.netlib.io.IOBuffer;

/**
 *
 * @author Kason Yang
 */
public class IOBufferTest {
    
    public IOBufferTest() {
    }
    
    @Test
    public void testBasic(){
        IOBuffer buff = IOBuffer.create(10);
        byte[] data = new byte[]{1,2,3,4,5};
        byte[] data2 = new byte[]{6,7,8,9,10};
        byte[] byteBuffer = new byte[5];
        buff.push(data);
        assertEquals(5, buff.getReadableSize());
        buff.poll(byteBuffer);
        assertEquals(0, buff.getReadableSize());
        assertArrayEquals(data, byteBuffer);
        buff.push(data2);
        buff.poll(byteBuffer);
        assertArrayEquals(data2,byteBuffer);
    }
    
}
