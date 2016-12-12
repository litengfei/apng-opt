package li.tengfei.apng.opt.optimizer;

import li.tengfei.apng.opt.builder.AngChunkData;
import li.tengfei.apng.opt.builder.AngData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

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
        ArrayList<Palette> palettes = genPalettes(ang);
        palettes = optmizePalette(palettes);
        return null;
    }


    /**
     * @param palettes
     * @return
     */
    private ArrayList<Palette> optmizePalette(ArrayList<Palette> palettes) {


        return null;

    }

    /**
     * generate palettes with Merged Color to the most previous one
     */
    private ArrayList<Palette> genPalettes(AngData ang) {
        Palette pre = null;
        ArrayList<Palette> palettes = new ArrayList<>();
        ArrayList<Color> newColors = new ArrayList<>();
        int chunkIndex = -1;
        for (AngChunkData chunk : ang.getChunks()) {
            chunkIndex++;
            if (chunk.getTypeCode() != CODE_PLTE) continue;

            if (pre == null) {
                pre = new Palette(chunkIndex);
            }

            // get new appeared colors in this frame
            byte[] data = chunk.getData();
            int count = entriesCount(data);
            newColors.clear();
            for (int i = 0; i < count; i++) {
                Color newColor = new Color(data, i);
                for (Color color : pre.colors) {
                    if (newColor.sameAs(color)) {
                        newColor = null;
                        break;
                    }
                }
                if (newColor != null) {
                    newColors.add(newColor);
                }
            }

            if (pre.colors.size() + newColors.size() > 256) {
                palettes.add(pre);
                pre = new Palette(chunkIndex);
                pre.colors.addAll(newColors);
            } else {
                pre.colors.addAll(newColors);
            }
        }
        if (pre != null) palettes.add(pre);

        for (Palette p : palettes) {
            log.debug(String.format("chunk: %d, frame: %d, colors: %d",
                    p.chunkIndex,
                    ang.getChunks().get(p.chunkIndex).getFrameIndex(),
                    p.colors.size()));
        }

        return palettes;
    }

    private int entriesCount(byte[] chunkData) {
        return (chunkData.length - 12) / 3;
    }

    private static class Palette {
        ArrayList<Color> colors = new ArrayList<>();
        int chunkIndex;

        public Palette(int chunkIndex) {
            this.chunkIndex = chunkIndex;
        }
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
            return diff <= 6;
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
            color += (data[offset + 1] & 0xFF) << 8;
            color += (data[offset + 2] & 0xFF);
            return color;
        }
    }
}
