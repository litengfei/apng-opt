package li.tengfei.apng.opt.builder;

import java.util.Arrays;

import static li.tengfei.apng.base.ApngConst.CODE_IHDR;

/**
 * Png Chunk Data
 *
 * @author ltf
 * @since 16/12/7, 上午11:54
 */
public class PngChunkData {

    byte[] data;

    int typeCode;

    public PngChunkData(byte[] data, int typeCode) {
        this.data = data;
        this.typeCode = typeCode;
    }

    public PngChunkData(PngChunkData chunkData) {
        this(chunkData.data, chunkData.typeCode);
    }

    public byte[] getData() {
        return data;
    }

    public int getTypeCode() {
        return typeCode;
    }

    @Override
    public boolean equals(Object obj) {
        // check pointer
        if (this == obj) return true;
        // check type
        if (!(obj instanceof PngChunkData)) return false;
        PngChunkData that = (PngChunkData) obj;
        // check typeCode
        if (this.typeCode != that.typeCode) return false;
        // check data
        if (this.typeCode == CODE_IHDR)
            return ihdrEquals(this.data, that.data);
        else
            return Arrays.equals(this.data, that.data);
    }

    /**
     * idhr equals don't include compare width and height
     */
    private boolean ihdrEquals(byte[] a, byte[] b) {
        for (int i = 16; i < 21; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }

        return true;
    }
}
