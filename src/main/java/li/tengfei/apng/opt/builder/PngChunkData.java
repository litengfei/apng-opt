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

    /**
     * fcTL data equals test [exclude seq_num\delay_num\delay_den and crc sections]
     */
    public static boolean fctlEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != 38 || b.length != 38) return false;
        // skip seq_number and crc
        for (int i = 12; i < 28; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        for (int i = 32; i < 34; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
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
