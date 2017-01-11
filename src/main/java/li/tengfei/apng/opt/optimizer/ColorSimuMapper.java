package li.tengfei.apng.opt.optimizer;

import java.awt.*;
import java.util.ArrayList;
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

    /**
     * get MedianColor of the colors
     */
    private static Color getMedianColor(java.util.List<Color> colors) {
        long R = 0, G = 0, B = 0, A = 0, W = 0;
        // compute center a,r,g,b
        for (Color color : colors) {
            R += color.getRed();
            G += color.getGreen();
            B += color.getBlue();
            A += color.getAlpha();
            W++;
        }

        Color center = new Color(
                Math.round(R / W),
                Math.round(G / W),
                Math.round(B / W),
                Math.round(A / W));
        return center;
    }

    @Override
    protected void genIndexedImage(Color[] pixels, int height, Map<Color, Color> colorMap, HashMap<Color, Integer> colorIndex, Mapping mapping) {
        super.genIndexedImage(pixels, height, colorMap, colorIndex, mapping);
        int width = pixels.length / height;
        initCache(width, height);
        Color[][] orig = new Color[height][width];
        byte[][] indexed = new byte[height][width];
        int[][][] gradient = new int[height][width][4];
        int[][] gradientArea = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                gradientArea[y][x] = -1; // not in gradient area
            }
        }

        // count gradient pixels at four direction ,
        // index:  0: top, 1: left, 2: right, 3: bottom
        // value: -1 means not init, >= 0 means gradient pixels at the direction
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                orig[y][x] = pixels[y * width + x];
                indexed[y][x] = mapping.pixelIndexes[y * width + x];
                for (int d = 0; d < 4; d++) gradient[y][x][d] = -1;
            }
        }

        // gradient detection
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // update top,bottom count
                if (y == 0 || gradient[y - 1][x][3] == 0) {
                    gradient[y][x][0] = 0;
                    gradient[y][x][3] = gradientPixelsCount(orig, x, y, 3);
                } else {
                    gradient[y][x][0] = gradient[y - 1][x][0] + 1;
                    gradient[y][x][3] = gradient[y - 1][x][3] - 1;
                }

                // update left,right count
                if (x == 0 || gradient[y][x - 1][2] == 0) {
                    gradient[y][x][1] = 0;
                    gradient[y][x][2] = gradientPixelsCount(orig, x, y, 2);
                } else {
                    gradient[y][x][1] = gradient[y][x - 1][1] + 1;
                    gradient[y][x][2] = gradient[y][x - 1][2] - 1;
                }
            }
        }

        // gradient detection
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // select max length gradient direction, and the start color not equals the end color
                int beginV = 0, endV = 0, deltaV = 0;
                if (gradient[y][x][0] > 0 && gradient[y][x][3] > 0) {
                    beginV = y - gradient[y][x][0];
                    endV = y + gradient[y][x][3];
                    if (indexed[beginV][x] != indexed[endV][x]) {
                        deltaV = endV - beginV;
                    }
                }
                int beginH = 0, endH = 0, deltaH = 0;
                if (gradient[y][x][1] > 0 && gradient[y][x][2] > 0) {
                    beginH = x - gradient[y][x][1];
                    endH = x + gradient[y][x][2];
                    if (indexed[y][beginH] != indexed[y][endH]) {
                        deltaH = endH - beginH;
                    }
                }

                int mark = -1;
                if (deltaV > 0 || deltaH > 0) {
                    markGradientArea(gradient, gradientArea, x, y, ++mark);
                }
                // mapping.pixelIndexes[width * y + x] = (byte) (139 & 0xff);
            }
        }


        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (gradientArea[y][x]>=0)
                    mapping.pixelIndexes[width * y + x] = (byte) ((139+gradientArea[y][x]) & 0xff);
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
        int height = colors.length;
        int width = colors[0].length;
        int count = 0;

        if (direction == 2) {
            for (int x = cx + 1; x < width - 1; x++) {
                if (isGradient(colors[cy][x - 1], colors[cy][x], colors[cy][x + 1])) {
                    count++;
                } else {
                    break;
                }
            }
            if (count > 0) count++; // count last pixel
        } else if (direction == 3) {
            for (int y = cy + 1; y < height - 1; y++) {
                if (isGradient(colors[y - 1][cx], colors[y][cx], colors[y + 1][cx])) {
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
     * mark all pixels in the same gradient area
     *
     * @param gradientArea mark
     * @param x            current x
     * @param y            current y
     * @param mark         mark id
     */
    private void markGradientArea(int[][][] gradient, int[][] gradientArea, int x, int y, int mark) {
        if (gradientArea[y][x] >= 0) return; // already marked
        gradientArea[y][x] = mark;
        for (int xx = x - gradient[y][x][1]; xx <= x + gradient[y][x][2]; xx++) {
            markGradientArea(gradient, gradientArea, xx, y, mark);
        }
        for (int yy = y - gradient[y][x][0]; yy <= y + gradient[y][x][3]; yy++) {
            markGradientArea(gradient, gradientArea, x, yy, mark);
        }
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
            if (direction == 2) cacheColor(indexed[y][i]);
            else cacheColor(indexed[i][x]);
        }


        int max = end - begin;
        int pos = direction == 2 ? x - begin : y - begin;
        int colorA = pos * (mCacheCount - 1) / max;

        int rateB = pos * (mCacheCount - 1) % max;
        int rateAll = max - 1;

        System.out.println(rateB + "  " + rateAll);

        if (rateB == 0) return mCacheColors[colorA];
        else if (rateB == rateAll) return mCacheColors[colorA + 1];
        else if (rateB * 2 > rateAll) {
            return mCacheColors[(x + y) % (rateB / (rateAll - rateB) + 1) == 0 ? colorA : colorA + 1];
        } else {
            return mCacheColors[(x + y) % ((rateAll - rateB) / rateB + 1) == 0 ? colorA + 1 : colorA];
        }
    }

    private byte smoothPickColor(byte[][] indexed, int[][][] gradient, int x, int y, Color[] ctable) {
        ArrayList<Color> colors = new ArrayList<>();
        for (int xx = x - gradient[y][x][1]; xx <= x + gradient[y][x][2]; xx++) {
            colors.add(ctable[indexed[y][xx] & 0xff]);
        }
        for (int yy = y - gradient[y][x][0]; yy <= y + gradient[y][x][3]; yy++) {
            colors.add(ctable[indexed[yy][x] & 0xff]);
        }

        Color color = getMedianColor(colors);
        int dist = Integer.MAX_VALUE;
        int idx = 0;
        for (int i = 0; i < ctable.length; i++) {
            int dis = ColorUtils.distance(ctable[i], color);
            if (dis < dist) {
                dist = dis;
                idx = i;
            }
        }

        return (byte) (idx & 0xff);

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
