package li.tengfei.apng.ext;

import li.tengfei.apng.base.DInt;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Writer for DInt
 *
 * @author ltf
 * @since 16/12/9, 下午3:32
 */
public class DIntWriter extends DInt {

    public static final int HAVE_NEXT_BYTE_MASK = -128;
    private byte[] data = new byte[4];

    public DIntWriter() {
    }

    public DIntWriter(int value) {
        setValue(value);
    }

    public void setValue(int value) {
        this.value = value;
        size = 0;
        byte v = (byte) (value & VALUE_MASK);
        value >>= 7;
        data[size++] = value == 0 ? v : (byte) (v | HAVE_NEXT_BYTE_MASK);
        while (value != 0) {
            v = (byte) (value & VALUE_MASK);
            value >>= 7;
            data[size++] = value == 0 ? v : (byte) (v | HAVE_NEXT_BYTE_MASK);

            if (size == 3 && value != 0) {
                data[size++] = (byte) (value & 0xff);
                if (value >> 8 != 0) throw new IllegalStateException("DInt value over range");
                break;
            }
        }
    }

    /**
     * write DInt to array
     *
     * @param dst    dest array
     * @param offset dest array offset
     * @return writed size
     */
    public int write(byte[] dst, int offset) {
        System.arraycopy(data, 0, dst, offset, size);
        return size;
    }

    /**
     * write DInt to OutputStream
     *
     * @return writed size
     */
    public int write(OutputStream os) throws IOException {
        os.write(data, 0, size);
        return size;
    }
}
