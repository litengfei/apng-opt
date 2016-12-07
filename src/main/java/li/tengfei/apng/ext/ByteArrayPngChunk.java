package li.tengfei.apng.ext;

import li.tengfei.apng.base.ApngDataSupplier;

/**
 * @author ltf
 * @since 16/10/13, 下午12:37
 */
public class ByteArrayPngChunk implements ApngDataSupplier {
    // data buffer
    private final byte[] buf;

    // read pointer
    private int pos;

    public ByteArrayPngChunk(byte[] buf) {
        this.buf = buf;
    }

    /**
     * read the 4-bytes before offset as a big-endian int
     */
    private int readInt(byte[] buf, int pos) {
        return (buf[pos - 4] & 0xFF) << 24 | (buf[pos - 3] & 0xFF) << 16 | (buf[pos - 2] & 0xFF) << 8 | (buf[pos - 1] & 0xFF);
    }

    /**
     * read the 2-bytes before offset as a big-endian int
     */
    private short readShort(byte[] buf, int pos) {
        return (short) ((buf[pos - 2] & 0xFF) << 8 | (buf[pos - 1] & 0xFF));
    }

    /**
     * read the 1-byte before offset as a big-endian int
     */
    private byte readByte(byte[] buf, int pos) {
        return (byte) (buf[pos - 1] & 0xFF);
    }

    @Override
    public int readInt() {
        pos += 4;
        return readInt(buf, pos);
    }

    @Override
    public short readShort() {
        pos += 2;
        return readShort(buf, pos);
    }

    @Override
    public byte readByte() {
        return readByte(buf, ++pos);
    }

    @Override
    public void move(int distance) {
        pos += distance;
    }
}
