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
        int[] dists = new int[4 * R * R];
        byte[] indexes = mapping.pixelIndexes;
        mapping.pixelIndexes = new byte[mapping.pixelIndexes.length];
        //Color[] centers = new Color[R * R];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pa = y * width + x;
                Color A = pixels[pa];
                int yl = 0 < y - R ? y - R : 0;
                int yh = height - 1 < y + R ? height - 1 : y + R;
                int xl = 0 < x - R ? x - R : 0;
                int xh = width - 1 < x + R ? width - 1 : x + R;

                int count = 0, distSum = 0;
                for (int yy = yl; yy < yh; yy++) {
                    for (int xx = xl; xx < xh; xx++) {
                        // calculate circle range
                        if ((xx - x) * (xx - x) + (yy - y) * (yy - y) > R * R) continue;
                        int pb = yy * width + xx;
                        Color B = pixels[pb];
                        distSum += ColorUtils.distance(A, B);
                        count++;
                    }
                }

                int distAvg = distSum / count + 1;
                int i = 0;
                for (int yy = yl; yy < yh; yy++) {
                    for (int xx = xl; xx < xh; xx++) {
                        // calculate circle range
                        if ((xx - x) * (xx - x) + (yy - y) * (yy - y) > R * R) continue;
                        int pb = yy * width + xx;
                        Color B = pixels[pb];
                        int dist = ColorUtils.distance(A, B);
                        if (dist > distAvg || dist > MAX_DIST) continue;
                        centers[i] = indexes[pb];
                        dists[i++] = (R * R) / ((xx - x) * (xx - x) + (yy - y) * (yy - y) + 1);
                    }
                }

                if (i > 0) {
                    int disAll = 0;
                    for (int dis : dists) disAll += dis;
                    int idx = rand.nextInt(i);
                    disAll -= dists[i];
                    while (disAll > 0) {
                        idx = rand.nextInt(i);
                        disAll -= dists[idx];
                    }
                    mapping.pixelIndexes[pa] = centers[idx];
                } else {
                    mapping.pixelIndexes[pa] = indexes[pa];
                }
            }
        }
    }
}
