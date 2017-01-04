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
    private static final int R = 8;
    private static final int MAX_DIST = 128;
    private Random rand = new Random();

    @Override
    protected void genIndexedImage(Color[] pixels, int height, Map<Color, Color> colorMap, HashMap<Color, Integer> colorIndex, Mapping mapping) {
        super.genIndexedImage(pixels, height, colorMap, colorIndex, mapping);
        int width = pixels.length / height;
        Color[][] orig = new Color[width][height];
        byte[][] indexed = new byte[width][height];
        int[][][] gradient = new int[width][height][4];
        // count gradient pixels at four direction ,
        // index:  0: top, 1: left, 2: right, 3: bottom
        // value: -1 means not init, >= 0 means gradient pixels at the direction
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                orig[x][y] = pixels[y * width + x];
                indexed[x][y] = mapping.pixelIndexes[y * width + x];
                for (int d = 0; d < 4; d++) gradient[x][y][d] = -1;
            }
        }

        // gradient detection
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // update top count
                if (y == 0 || gradient[x][y][3] == 0) gradient[x][y][0] = 0;
                else gradient[x][y][0] = gradient[x][y][0] + 1;

                // update left count
                if (x == 0 || gradient[x][y][2] == 0) gradient[x][y][1] = 0;
                else gradient[x][y][1] = gradient[x][y][1] + 1;

                // update right count
                gradient[x][y][2] = gradientPixelsCount(orig, x, y, 2);

                // update bottom count
                gradient[x][y][3] = gradientPixelsCount(orig, x, y, 3);
            }
        }

        // gradient detection
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int g = 0;
                for (int d = 0; d < 4; d++) g += gradient[x][y][d];
                if (g > 3) {
                    mapping.pixelIndexes[width * y + x] = (byte)((mapping.colorTable.length-1) & 0xff);
                }

            }
        }
    }

    /**
     * get gradient Pixels Count
     *
     * @param colors    image map
     * @param cx        current position x
     * @param cy        current position x
     * @param direction direction, 2: right, 3: bottom
     * @return gradient Pixels Count
     */
    private int gradientPixelsCount(Color[][] colors, int cx, int cy, int direction) {
        int width = colors.length;
        int height = colors[0].length;
        int count = 0;

        if (direction == 2) {
            boolean lastPixelIsGradient = cx < width - 2;
            for (int x = cx + 1; x < width - 1; x++) {
                if (isGradient(colors[x - 1][cy], colors[x][cy], colors[x + 1][cy])) {
                    count++;
                } else {
                    lastPixelIsGradient = false;
                    break;
                }
            }
            if (lastPixelIsGradient) count++;
        } else if (direction == 3) {
            boolean lastPixelIsGradient = cy < height - 2;
            for (int y = cy + 1; y < height - 1; y++) {
                if (isGradient(colors[cx][y - 1], colors[cx][y], colors[cx][y + 1])) {
                    count++;
                } else {
                    lastPixelIsGradient = false;
                    break;
                }
            }
            if (lastPixelIsGradient) count++;
        }

        return count;
    }

    /**
     * check is the three colors gradient
     */
    private boolean isGradient(Color c1, Color c2, Color c3) {
        int dR3 = (c3.getRed() -    c1.getRed());
        int dG3 = (c3.getGreen() -  c1.getGreen());
        int dB3 = (c3.getBlue() -   c1.getBlue());
        int dA3 = (c3.getAlpha() -  c1.getAlpha());
        // color not changed
        if (dR3 * dR3 + dG3 * dG3 + dB3 * dB3 + dA3 * dA3 < 1) return false;

        int dR1 = (c2.getRed() - c1.getRed());
        int dG1 = (c2.getGreen() - c1.getGreen());
        int dB1 = (c2.getBlue() - c1.getBlue());
        int dA1 = (c2.getAlpha() - c1.getAlpha());

        int dR2 = (c3.getRed() - c2.getRed());
        int dG2 = (c3.getGreen() - c2.getGreen());
        int dB2 = (c3.getBlue() - c2.getBlue());
        int dA2 = (c3.getAlpha() - c2.getAlpha());



        int dR = dR2 - dR1;
        int dG = dG2 - dG1;
        int dB = dB2 - dB1;
        int dA = dA2 - dA1;
        return dR * dR + dG * dG + dB * dB + dA * dA < 4;
    }


    protected void olgGenIndexedImage(Color[] pixels, int height, Map<Color, Color> colorMap, HashMap<Color, Integer> colorIndex, Mapping mapping) {
        super.genIndexedImage(pixels, height, colorMap, colorIndex, mapping);

        int width = pixels.length / height;

        byte[] centers = new byte[4 * R * R];
        int[] dists = new int[4 * R * R];
        byte[] indexes = mapping.pixelIndexes;
        mapping.pixelIndexes = new byte[mapping.pixelIndexes.length];

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
                    int idx = 0;
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
