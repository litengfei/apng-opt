package li.tengfei.apng.opt.optimizer;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * color simulation mapper
 *
 * @author ltf
 * @since 17/1/3, 下午12:37
 */
public class ColorSimuMapper extends BaseColorMapper {
    private static final int R = 10;
    private static final int MAX_DIST = 128;
    private Random rand = new Random();

    @Override
    protected void genIndexedImage(Color[] pixels, int height, Map<Color, Color> colorMap, HashMap<Color, Integer> colorIndex, Mapping mapping) {
        super.genIndexedImage(pixels, height, colorMap, colorIndex, mapping);

        int width = pixels.length / height;

        byte[] centers = new byte[4 * R * R];
        byte[] indexes = new byte[mapping.pixelIndexes.length];
        //Color[] centers = new Color[R * R];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pa = y * width + x;
                Color A = pixels[pa];
                int yl = 0 < y - R ? y - R : 0;
                int yh = height - 1 < y + R ? height - 1 : y + R;
                int xl = 0 < x - R ? x - R : 0;
                int xh = width - 1 < x + R ? width - 1 : x + R;

                int i = 0;
                for (int yy = yl; yy < yh; yy++) {
                    for (int xx = xl; xx < xh; xx++) {
                        // calculate circle range
                        if ((xx - x) * (xx - x) + (yy - y) * (yy - y) > R * R) continue;
                        int pb = yy * width + xx;
                        Color B = mapping.colorTable[mapping.pixelIndexes[pb] & 0xff];
                        if (ColorUtils.distance(A, B) > MAX_DIST) continue;
                        centers[i++] = mapping.pixelIndexes[pb];
                    }
                }
                if (i > 0)
                    indexes[pa] = centers[rand.nextInt(i)];
                else
                    indexes[pa] = mapping.pixelIndexes[pa];
            }
        }
        mapping.pixelIndexes = indexes;
    }
}
