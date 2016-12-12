package li.tengfei.apng.opt.optimizer;

import li.tengfei.apng.opt.builder.AngData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;

import static li.tengfei.apng.base.ApngConst.CODE_PLTE;

/**
 * Optimizer for the ANG frames palette
 *
 * @author ltf
 * @since 16/12/12, 下午2:05
 */
public class PaletteOptimizer implements AngOptimizer {
    private static final int COLOR_B = 0x1;
    private static final int COLOR_G = 0x100;
    private static final int COLOR_R = 0x10000;
    private static final Logger log = LoggerFactory.getLogger(PaletteOptimizer.class);

    @Override
    public AngData optimize(AngData ang) {

        int[] allEntries = new int[1];
        allEntries[0] = 0;
        HashSet<Integer> colors = new HashSet<>();
        HashSet<Color> sameColors = new HashSet<>();

        ang.getChunks().forEach(it -> {
            if (it.getTypeCode() == CODE_PLTE) {
                byte[] data = it.getData();
                int count = entriesCount(data);
                for (int i = 0; i < count; i++) {
                    allEntries[0]++;
                    colors.add(readEntry(data, i));
                    sameColors.add(new Color(data, i));
                }
                log.debug(String.format("frame: %d, count: %d, all: %d, colors: %d, sameColors: %d",
                        it.getFrameIndex(),
                        count,
                        allEntries[0],
                        colors.size(),
                        sameColors.size()));
            }
        });

        return null;
    }

    private int entriesCount(byte[] chunkData) {
        return (chunkData.length - 12) / 3;
    }

    private int readEntry(byte[] chunkData, int index) {
        int off = 8 + index * 3;
        int color = (chunkData[off++] & 0xFF) << 16;
        color += (chunkData[off++] & 0xFF) << 8;
        color += (chunkData[off] & 0xFF);
        return color;
    }

    private void writeEntry(byte[] chunkData, int index, int color) {
        int off = 8 + index * 3;
        chunkData[off++] = (byte) (color >> 16 & 0xFF);
        chunkData[off++] = (byte) (color >> 8 & 0xFF);
        chunkData[off] = (byte) (color & 0xFF);
    }


    /**
     * gen same color
     *
     * @param color
     * @param sameColor
     */
    private void sameColor(int color, int[] sameColor) {


    }

    private static class Color {
        private byte[] data;
        private int offset;

        public Color(byte[] data, int index) {
            this.data = data;
            this.offset = 8 + index * 3;
        }

        public boolean sameAs(Color color) {
            int diff = Math.abs(this.data[this.offset] - color.data[color.offset]);
            diff += Math.abs(this.data[this.offset + 1] - color.data[color.offset + 1]);
            diff += Math.abs(this.data[this.offset + 2] - color.data[color.offset + 2]);
            return diff <= 0;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Color) {
                Color color = (Color) obj;
                return this.data[this.offset] == color.data[color.offset]
                        && this.data[this.offset + 1] == color.data[color.offset + 1]
                        && this.data[this.offset + 2] == color.data[color.offset + 2];
            }
            return false;
        }

        @Override
        public int hashCode() {
            int color = (data[offset] & 0xFF) << 16;
            color += (data[offset+1] & 0xFF) << 8;
            color += (data[offset+2] & 0xFF);
            return color;
        }
    }
}
