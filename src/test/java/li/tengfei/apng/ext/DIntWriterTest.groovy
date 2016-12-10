package li.tengfei.apng.ext

import li.tengfei.apng.base.DInt
import org.testng.annotations.Test

import static org.testng.Assert.assertEquals

/**
 *
 * @author ltf
 * @since 16/12/9, 下午4:24
 */
class DIntWriterTest {

    @Test
    void testDInt() {
        byte[] cache = new byte[10]
        byte[] readedSize = new byte[1]
        int size = -1
        DIntWriter writer = new DIntWriter()
        DInt dInt = new DInt()
        for (int i = DInt.MIN_DINT_VALUE; i <= DInt.MAX_DINT_VALUE; i++) {
            dInt.reset()
            writer.setValue(i)

            if (size != writer.size) {
                size = writer.size
                println(String.format("size: %d  i: %d  v: %d ", size, writer.value, i))
            }

            assertEquals writer.write(cache, 3), size
            assertEquals dInt.readValue(cache, 3, readedSize), i
            assertEquals readedSize[0], size
            assertEquals writer.value, dInt.value
        }
    }
}
