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

    /**
     * the same color detection if:
     * the first and last color's channel value square_sum not big than this.
     * when the value turn bigger, the smaller gradient pixels will be detected
     */
    private static final int SAME_COLOR_DELTA = 1;
    /**
     * the gradient color detection if:
     * the second-first and last-second color's channel value change square_sum not big than this.
     * when the value turn bigger, the more gradient pixels will be detected, but lost more edge
     */
    private static final int GRADIENT_DELTA = 3;

    private Random rand = new Random();

    private byte[] mCacheColors = {};
    private int mCacheCount = 0;


    @Override
    protected void genIndexedImage(Color[] pixels, int height, Map<Color, Color> colorMap, HashMap<Color, Integer> colorIndex, Mapping mapping) {
        super.genIndexedImage(pixels, height, colorMap, colorIndex, mapping);
        int width = pixels.length / height;
        initCache(width, height);
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
                // update top,bottom count
                if (y == 0 || gradient[x][y - 1][3] == 0) {
                    gradient[x][y][0] = 0;
                    gradient[x][y][3] = gradientPixelsCount(orig, x, y, 3);
                } else {
                    gradient[x][y][0] = gradient[x][y - 1][0] + 1;
                    gradient[x][y][3] = gradient[x][y - 1][3] - 1;
                }

                // update left,right count
                if (x == 0 || gradient[x - 1][y][2] == 0) {
                    gradient[x][y][1] = 0;
                    gradient[x][y][2] = gradientPixelsCount(orig, x, y, 2);
                } else {
                    gradient[x][y][1] = gradient[x - 1][y][1] + 1;
                    gradient[x][y][2] = gradient[x - 1][y][2] - 1;
                }
            }
        }

        // gradient detection
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // select max length gradient direction, and the start color not equals the end color
                int beginV = 0, endV = 0, deltaV = 0;
                if (gradient[x][y][0] > 0 && gradient[x][y][3] > 0) {
                    beginV = y - gradient[x][y][0];
                    endV = y + gradient[x][y][3];
                    if (indexed[x][beginV] != indexed[x][endV]) {
                        deltaV = endV - beginV;
                    }
                }
                int beginH = 0, endH = 0, deltaH = 0;
                if (gradient[x][y][1] > 0 && gradient[x][y][2] > 0) {
                    beginH = x - gradient[x][y][1];
                    endH = x + gradient[x][y][2];
                    if (indexed[beginH][y] != indexed[endH][y]) {
                        deltaH = endH - beginH;
                    }
                }

//                if (deltaV > deltaH && deltaV > 0) {
//                    mapping.pixelIndexes[width * y + x] = pickGradientColor(indexed, x, y, 3, beginV, endV);
//                } else if (deltaH > 0) {
//                    mapping.pixelIndexes[width * y + x] = pickGradientColor(indexed, x, y, 2, beginH, endH);
//                }
                // mapping.pixelIndexes[width * y + x] = (byte) (139 & 0xff);
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
            for (int x = cx + 1; x < width - 1; x++) {
                if (isGradient(colors[x - 1][cy], colors[x][cy], colors[x + 1][cy])) {
                    count++;
                } else {
                    break;
                }
            }
            if (count > 0) count++; // count last pixel
        } else if (direction == 3) {
            for (int y = cy + 1; y < height - 1; y++) {
                if (isGradient(colors[cx][y - 1], colors[cx][y], colors[cx][y + 1])) {
                    count++;
                } else {
                    break;
                }
            }
            if (count > 0) count++;  // count last pixel
        }

        return count;
    }

    /**
     * check is the three colors gradient
     */
    private boolean isGradient(Color c1, Color c2, Color c3) {
        int dR3 = (c3.getRed() - c1.getRed());
        int dG3 = (c3.getGreen() - c1.getGreen());
        int dB3 = (c3.getBlue() - c1.getBlue());
        int dA3 = (c3.getAlpha() - c1.getAlpha());
        // color not changed
        if (dR3 * dR3 + dG3 * dG3 + dB3 * dB3 + dA3 * dA3 < SAME_COLOR_DELTA) return false;

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
        return dR * dR + dG * dG + dB * dB + dA * dA < GRADIENT_DELTA;
    }


    private void initCache(int width, int height) {
        int i = width > height ? width : height;
        if (i > mCacheColors.length) {
            mCacheColors = new byte[i];
        }
    }

    private void cacheColor(byte color) {
        boolean notExists = true;
        for (int i = 0; i < mCacheCount; i++) {
            if (mCacheColors[i] == color) {
                notExists = false;
                break;
            }
        }
        if (notExists) mCacheColors[mCacheCount++] = color;
    }

    /**
     * picked gradient color for current position
     *
     * @param indexed   indexed color map
     * @param x         current position x
     * @param y         current position y
     * @param direction 2 for Horizontal, else(3) for Vertical
     * @param begin     gradient begin index
     * @param end       gradient end index
     * @return selected color
     */
    private byte pickGradientColor(byte[][] indexed, int x, int y, int direction, int begin, int end) {
        mCacheCount = 0;
        for (int i = begin; i <= end; i++) {
            if (direction == 2) cacheColor(indexed[i][y]);
            else cacheColor(indexed[x][i]);
        }


        int max = end - begin;
        int pos = direction == 2 ? x - begin : y - begin;
        int colorA = pos * (mCacheCount - 1) / max;

        int rateB = pos * (mCacheCount - 1) % max;
        int rateAll = max - 1;

        if (rateB == 0) return mCacheColors[colorA];
        else if (rateB == rateAll) return mCacheColors[colorA + 1];
        else if (rateB * 2 > rateAll) {
            return mCacheColors[(x + y) % (rateB / (rateAll - rateB) + 1) == 0 ? colorA : colorA + 1];
        } else {
            return mCacheColors[(x + y) % ((rateAll - rateB) / rateB + 1) == 0 ? colorA + 1 : colorA];
        }
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
