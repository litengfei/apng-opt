package li.tengfei.apng.opt.optimizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;


/**
 * K-Means++ Color Reducer
 *
 * @author ltf
 * @since 16/12/14, 上午9:52
 */
public class KMeansPpReducer implements ColorReducer {
    private static final Logger log = LoggerFactory.getLogger(KMeansPpReducer.class);
    private static final int COLOR_BYTES = 4;
    private Random rand = new Random();

    // all pixels count
    private int mPixelsCount;

    // all colors index
    private int[] mColors;
    // color data bytes
    private byte[] mColorBytes;
    // color appeared counts
    private int[] mColorCounts;

    //    // color mapping to center indexes
//    private int[] mColorIndexes;
//
//    // KMeans centers index point to mColorBytes
//    private int[] mCenterIndexes;
//    // center color pixels count
//    private int[] mCenterCounts;

    /**
     * reduce color use k-means++ algorithm
     */
    @Override
    public Map<Color, Color> reduce(Color[] pixels, int target) {
        mPixelsCount = pixels.length;

        // count distinct colors
        HashMap<Color, Integer> countMap = new HashMap<Color, Integer>();
        for (Color p : pixels) {
            if (countMap.containsKey(p)) {
                countMap.put(p, countMap.get(p) + 1);
            } else {
                countMap.put(p, 1);
            }
        }

        // create member variables
        mColorBytes = new byte[countMap.size() * COLOR_BYTES];
        mColorCounts = new int[countMap.size()];
        mColors = new int[countMap.size()];

        // set colors [ sorted by count ]
        ArrayList<ColorCount> colorCounts = new ArrayList<>(countMap.size());
        for (Map.Entry<Color, Integer> e : countMap.entrySet()) {
            colorCounts.add(new ColorCount(e.getKey(), e.getValue()));
        }
        Collections.sort(colorCounts);
        for (int i = 0; i < colorCounts.size(); i++) {
            toColorBytes(colorCounts.get(i).color, i);
            mColorCounts[i] = colorCounts.get(i).count;
            mColors[i] = i;
        }

        int[] indexes = new int[countMap.size()];
        int[] centers = new int[target];

        // do reduce
        reduce(mColors, indexes, centers);

        HashMap<Color, Color> mapping = new HashMap<>(mColorCounts.length);
        for (int i = 0; i < mColorCounts.length; i++) {
            mapping.put(fromColorBytes(i), fromColorBytes(centers[indexes[i]]));
        }
        return mapping;
    }

    /**
     * set color to colorBytes array
     */
    private void toColorBytes(Color color, int index) {
        toColorBytes(color, mColorBytes, index);
    }

    /**
     * set color to colorBytes array
     */
    private void toColorBytes(Color color, byte[] colorBytes, int index) {
        int i = index * COLOR_BYTES;
        colorBytes[i++] = (byte) (color.getRed() & 0xff);
        colorBytes[i++] = (byte) (color.getGreen() & 0xff);
        colorBytes[i++] = (byte) (color.getBlue() & 0xff);
        colorBytes[i] = (byte) (color.getAlpha() & 0xff);
    }

    /**
     * get color from colorBytes array
     */
    private Color fromColorBytes(int index) {
        return fromColorBytes(mColorBytes, index);
    }

    /**
     * get color from colorBytes array
     */
    private Color fromColorBytes(byte[] colorBytes, int index) {
        int i = index * COLOR_BYTES;
        int R = colorBytes[i++] & 0xff;
        int G = colorBytes[i++] & 0xff;
        int B = colorBytes[i++] & 0xff;
        int A = colorBytes[i] & 0xff;
        return new Color(R, G, B, A);
    }

    /**
     * get color R from colorBytes array
     */
    private int rFromColorBytes(byte[] colorBytes, int index) {
        return colorBytes[index * COLOR_BYTES] & 0xff;
    }

    /**
     * get color G from colorBytes array
     */
    private int gFromColorBytes(byte[] colorBytes, int index) {
        return colorBytes[index * COLOR_BYTES + 1] & 0xff;
    }

    /**
     * get color B from colorBytes array
     */
    private int bFromColorBytes(byte[] colorBytes, int index) {
        return colorBytes[index * COLOR_BYTES + 2] & 0xff;
    }

    /**
     * get color A from colorBytes array
     */
    private int aFromColorBytes(byte[] colorBytes, int index) {
        return colorBytes[index * COLOR_BYTES + 3] & 0xff;
    }

    /**
     * reduce colors to centers(outColors)
     *
     * @param colors  input colors index
     * @param indexes output colors' mapping
     * @param centers output reduced colors
     */
    private void reduce(int[] colors, int[] indexes, int[] centers) {
        // init centers
        initCenters(colors, indexes, centers);

        int lastMinChanged = Integer.MAX_VALUE;
        int countOverLastMin = 0;
        while (cluster(colors, centers, indexes) > 0) {
            //splitMaxCenters(colors, counts, centers, indexes, 0.000005f);
            int changed = refreshCenters(colors, centers, indexes);

            // if current changed <= minChanged appeared N times ago, then stop
            if (countOverLastMin > 50 && changed <= lastMinChanged) break;

            if (changed < lastMinChanged) {
                lastMinChanged = changed;
                countOverLastMin = 0;
                log.debug("new min changed: " + changed);
            } else
                countOverLastMin++;
        }
    }

    /**
     * Random init center points (colors)
     */
    private void initCenters(int[] colors, int[] indexes, int[] centers) {
        int pixels = 0;
        // random init centers
        for (int i = 0; i < centers.length; i++) {
            int candidate = -1;
            while (candidate < 0) {
                candidate = randomPickColor(colors);
                // remove exists color
                for (int j = 0; j < i; j++) {
                    if (candidate == centers[i]) candidate = -1;
                }
            }
            centers[i] = candidate;
        }

        cluster(colors, centers, indexes);

        splitMaxCenters(colors, centers, indexes, 0.033f);
    }

    /**
     * get random pixels' color
     *
     * @return the Color Index in mColorBytes
     */
    private int randomPickColor(int[] colors) {
        int count = 0;
        if (colors == mColors) {
            count = mPixelsCount;
        } else {
            for (int i : colors) count += mColorCounts[i];
        }

        int candidateIndex = 0;
        while (count > 0) {
            candidateIndex = colors[rand.nextInt(colors.length)];
            count -= mColorCounts[candidateIndex];
        }
        return colors[candidateIndex];
    }

    /**
     * split max center
     *
     * @param splitRate split if the max * splitRate > min
     */
    private void splitMaxCenters(int[] colors, int[] centers, int[] indexes, float splitRate) {
        int[] centerCounts = new int[centers.length];
        for (int i = 0; i < indexes.length; i++) {
            int idx = indexes[i];
            centerCounts[idx] += mColorCounts[centers[idx]];
        }
        ArrayList<IndexCount> indexCounts = new ArrayList<>(centerCounts.length);
        for (int i = 0; i < centerCounts.length; i++) {
            indexCounts.add(new IndexCount(i, centerCounts[i]));
        }
        Collections.sort(indexCounts);

        for (int maxPos = 0, minPos = indexCounts.size() - 1; maxPos < minPos; maxPos++) {
            // split previous max count center, to replace last min count center, until max * splitRate < min
            if (indexCounts.get(maxPos).count * splitRate < indexCounts.get(minPos).count)
                break;

            if (splitMaxCenter(
                    colors,
                    centers,
                    indexes,
                    indexCounts.get(maxPos).index,
                    indexCounts.get(minPos).index)) {
                minPos--;
            }
        }
    }

    /**
     * split MAX CENTER centers[maxIdx] INTO centers[maxIdx], centers[minIdx]
     *
     * @return split success or not
     */
    private boolean splitMaxCenter(int[] colors, int[] centers, final int[] indexes, final int maxIdx, int minIdx) {
        int pixels = 0;
        long dist = 0;
        long avgDist = 0;
        long xR = 0, xG = 0, xB = 0, xA = 0, xPixels = 0;
        long yR = 0, yG = 0, yB = 0, yA = 0, yPixels = 0;
        int center = centers[maxIdx];

        // calculate avg ARGB
        for (int i = 0; i < indexes.length; i++) {
            if (indexes[i] != maxIdx) continue;
            int count = mColorCounts[colors[i]];
            dist += distance(center, colors[i]) * count;
            pixels += count;
        }
        if (pixels == 0) return false;
        avgDist = dist / pixels;

        // calculate avg ARGB
        for (int i = 0; i < indexes.length; i++) {
            if (indexes[i] != maxIdx) continue;
            int count = mColorCounts[colors[i]];
            if (distance(center, colors[i]) < avgDist) {
                xR += rFromColorBytes(mColorBytes, colors[i]) * count;
                xG += gFromColorBytes(mColorBytes, colors[i]) * count;
                xB += bFromColorBytes(mColorBytes, colors[i]) * count;
                xA += aFromColorBytes(mColorBytes, colors[i]) * count;
                xPixels += count;
            } else {
                yR += rFromColorBytes(mColorBytes, colors[i]) * count;
                yG += gFromColorBytes(mColorBytes, colors[i]) * count;
                yB += bFromColorBytes(mColorBytes, colors[i]) * count;
                yA += aFromColorBytes(mColorBytes, colors[i]) * count;
                yPixels += count;
            }
        }

        Color xColor = new Color(
                Math.round(xR / xPixels),
                Math.round(xG / xPixels),
                Math.round(xB / xPixels),
                Math.round(xA / xPixels));

        Color yColor = new Color(
                Math.round(yR / yPixels),
                Math.round(yG / yPixels),
                Math.round(yB / yPixels),
                Math.round(yA / yPixels));


        if (xColor.equals(yColor)) return false;

//        byte[] tmpColors = new byte[COLOR_BYTES * 2];
//        toColorBytes(xColor, 0);
//        toColorBytes(yColor, 1);
//
//        int disXMin = Integer.MAX_VALUE, disYMin = Integer.MAX_VALUE;
//        int x = 0, y = 0;
//        for (int i = 1; i < colors.length; i++) {
//            int disX = distance(tmpColors, 0, mColorBytes, colors[i]);
//            int disY = distance(tmpColors, 1, mColorBytes, colors[i]);
//            if (disX < disXMin) {
//                disXMin = disX;
//                x = i;
//            }
//            if (disY < disYMin) {
//                disYMin = disY;
//                y = i;
//            }
//        }

        centers[minIdx] = getColorIndex(xColor);
        centers[maxIdx] = getColorIndex(yColor);
        return true;
    }

    /**
     * find the nearest color_index in mColorBytes to Color
     *
     * @param color Color
     * @return index of mColorBytes
     */
    private int getColorIndex(Color color) {
        byte[] tmpColors = new byte[COLOR_BYTES];
        toColorBytes(color, 0);
        int disMin = Integer.MAX_VALUE;
        int idx = 0;
        for (int i = 1; i < mColors.length; i++) {
            int dis = distance(tmpColors, 0, mColorBytes, i);
            if (dis < disMin) {
                disMin = dis;
                idx = i;
            }
        }
        return idx;
    }

    /**
     * recompute centers
     *
     * @param colors  distinct colors
     * @param centers centers
     * @param indexes color mapping to indexes
     * @return color center changed counts
     */
    private int refreshCenters(int[] colors, int[] centers, int[] indexes) {
        int changed = 0;
        for (int i = 0; i < centers.length; i++) {
            long R = 0, G = 0, B = 0, A = 0, W = 0;
            // compute center a,r,g,b
            for (int j = 0; j < colors.length; j++) {
                if (indexes[j] != i) continue;
                long count = mColorCounts[colors[j]];
                R += rFromColorBytes(mColorBytes, colors[j]) * count;
                G += gFromColorBytes(mColorBytes, colors[j]) * count;
                B += bFromColorBytes(mColorBytes, colors[j]) * count;
                A += aFromColorBytes(mColorBytes, colors[j]) * count;
                W += count;
            }

            int center;
            if (W == 0) {
                center = randomPickColor(colors);
            } else {
                center = getColorIndex(new Color(
                        Math.round(R / W),
                        Math.round(G / W),
                        Math.round(B / W),
                        Math.round(A / W)));
            }

            if (center != centers[i]) {
                changed++;
                centers[i] = center;
            }
        }
        return changed;
    }

    /**
     * cluster colors to nearby centers
     *
     * @param colors  distinct colors
     * @param centers centers
     * @param indexes color mapping to indexes
     * @return color mapping changed counts
     */
    private int cluster(int[] colors, int[] centers, int[] indexes) {
        int changed = 0;
        for (int i = 0; i < colors.length; i++) {
            int minDist = Integer.MAX_VALUE;
            int minIdx = 0;

            for (int j = 0; j < centers.length; j++) {
                int dist = distance(colors[i], centers[j]);
                if (dist < minDist) {
                    minDist = dist;
                    minIdx = j;
                }
            }

            if (indexes[i] != minIdx) {
                indexes[i] = minIdx;
                changed++;
            }
        }
        return changed;
    }

    /**
     * calculate distance of two color (index in mColorBytes)
     */
    private int distance(int a, int b) {
        int dist = 0;
        int x = COLOR_BYTES * a, y = COLOR_BYTES * b;
        int delta = mColorBytes[x++] - mColorBytes[y++];
        dist += delta * delta;
        delta = mColorBytes[x++] - mColorBytes[y++];
        dist += delta * delta;
        delta = mColorBytes[x++] - mColorBytes[y++];
        dist += delta * delta;
        delta = mColorBytes[x] - mColorBytes[y];
        dist += delta * delta;
        return dist;
    }

    /**
     * calculate distance of two color (index in A\B)
     */
    private int distance(byte[] A, int a, byte[] B, int b) {
        int dist = 0;
        int x = COLOR_BYTES * a, y = COLOR_BYTES * b;
        int delta = A[x++] - B[y++];
        dist += delta * delta;
        delta = A[x++] - B[y++];
        dist += delta * delta;
        delta = A[x++] - B[y++];
        dist += delta * delta;
        delta = A[x] - B[y];
        dist += delta * delta;
        return dist;
    }

    /**
     * @param colors
     * @param a
     * @param b
     * @return
     */
    private int distance(Color[] colors, int a, int b) {
        if (a == b) return 0;
        else if (a > b) return distance(colors, b, a);
        return 0;
    }

    private static class IndexCount implements Comparable<IndexCount> {
        int index;
        int count;

        public IndexCount(int index, int count) {
            this.index = index;
            this.count = count;
        }

        @Override
        public int compareTo(IndexCount o) {
            return o.count - this.count;
        }
    }

    private static class ColorCount implements Comparable<ColorCount> {
        Color color;
        int count;

        public ColorCount(Color color, int count) {
            this.color = color;
            this.count = count;
        }

        @Override
        public int compareTo(ColorCount o) {
            return o.count - this.count;
        }
    }
}