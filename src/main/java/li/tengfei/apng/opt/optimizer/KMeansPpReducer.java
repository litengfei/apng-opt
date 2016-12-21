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
    private Random rand = new Random();
    private int mPixels;

    /**
     * reduce color use k-means++ algorithm
     */
    @Override
    public Map<Color, Color> reduce(Color[] pixels, int target) {
        mPixels = pixels.length;
        HashMap<Color, Integer> countMap = new HashMap<Color, Integer>();
        for (Color p : pixels) {
            if (countMap.containsKey(p)) {
                countMap.put(p, countMap.get(p) + 1);
            } else {
                countMap.put(p, 1);
            }
        }

        RGBA[] colors = new RGBA[countMap.size()];
        int[] counts = new int[countMap.size()];
        int i = 0;
        for (Map.Entry<Color, Integer> e : countMap.entrySet()) {
            colors[i] = new RGBA(e.getKey());
            counts[i++] = e.getValue();
        }
        int[] indexes = new int[countMap.size()];
        RGBA[] outcolors = new RGBA[target];

        reduce(colors, counts, indexes, outcolors);

        HashSet<RGBA> distinctOut = new HashSet<>();
        distinctOut.addAll(Arrays.asList(outcolors));
        log.debug("finally output colors count: " + distinctOut.size());

        HashMap<Color, Color> mapping = new HashMap<>(colors.length);
        for (int j = 0; j < colors.length; j++) {
            mapping.put(colors[j].asColor(), outcolors[indexes[j]].asColor());
        }
        return mapping;
    }

    /**
     * reduce colors to centers(outColors)
     *
     * @param colors  input colors
     * @param counts  input colors' count
     * @param indexes output colors' mapping
     * @param centers output reduced colors
     */
    private void reduce(RGBA[] colors, int[] counts, int[] indexes, RGBA[] centers) {
        // init centers
        initCenters(colors, counts, indexes, centers);

        int lastMinChanged = Integer.MAX_VALUE;
        int countOverLastMin = 0;
        long lastMillis = System.currentTimeMillis();
        while (cluster(colors, centers, indexes) > 0) {
            //splitMaxCenters(colors, counts, centers, indexes, 0.000005f);
            int changed = refreshCenters(colors, centers, counts, indexes);

            long millis = System.currentTimeMillis();
            log.debug("rounds millis: " + (millis - lastMillis));
            lastMillis = millis;

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
    private void initCenters(RGBA[] colors, int[] counts, int[] indexes, RGBA[] centers) {
        int pixels = 0;
        // random init centers
        for (int i = 0; i < centers.length; i++) {
            RGBA candidate = null;
            while (candidate == null) {

                candidate = randomPickRGBA(colors, counts);
                // remove exists RGBA
                for (int j = 0; j < i; j++) {
                    if (candidate.equals(centers[i])) candidate = null;
                }
            }
            centers[i] = candidate;
        }

        cluster(colors, centers, indexes);

        splitMaxCenters(colors, counts, centers, indexes, 0.033f);
    }

    private RGBA randomPickRGBA(RGBA[] colors, int[] counts) {
        int candidateCount = mPixels;
        int candidateIndex = 0;
        while (candidateCount > 0) {
            candidateIndex = rand.nextInt(counts.length);
            candidateCount -= counts[candidateIndex];
        }
        return colors[candidateIndex];
    }

    /**
     * split max center
     *
     * @param splitRate split if the max * splitRate > min
     */
    private void splitMaxCenters(RGBA[] colors, int[] counts, RGBA[] centers, int[] indexes, float splitRate) {
        int[] centerCounts = new int[centers.length];
        for (int i = 0; i < indexes.length; i++) {
            centerCounts[indexes[i]] += counts[i];
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
                    counts,
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
    private boolean splitMaxCenter(RGBA[] colors, int[] counts, RGBA[] centers, final int[] indexes, final int maxIdx, int minIdx) {
        int pixels = 0;
        long dist = 0;
        long avgDist = 0;
        long maxR = 0, maxG = 0, maxB = 0, maxA = 0, maxPixels = 0;
        long minR = 0, minG = 0, minB = 0, minA = 0, minPixels = 0;
        RGBA center = centers[maxIdx];

        // calculate avg ARGB
        for (int i = 0; i < indexes.length; i++) {
            if (indexes[i] != maxIdx) continue;
            dist += center.dist(colors[i]) * counts[i];
            pixels += counts[i];
        }
        avgDist = dist / pixels;

        // calculate avg ARGB
        for (int i = 0; i < indexes.length; i++) {
            if (indexes[i] != maxIdx) continue;
            int count = counts[i];
            if (center.dist(colors[i]) < avgDist) {
                minR += colors[i].r * count;
                minG += colors[i].g * count;
                minB += colors[i].b * count;
                minA += colors[i].a * count;
                minPixels += count;
            } else {
                maxR += colors[i].r * count;
                maxG += colors[i].g * count;
                maxB += colors[i].b * count;
                maxA += colors[i].a * count;
                maxPixels += count;
            }
        }

        RGBA minRGBA = new RGBA(
                Math.round(minR / minPixels),
                Math.round(minG / minPixels),
                Math.round(minB / minPixels),
                Math.round(minA / minPixels));

        RGBA maxRGBA = new RGBA(
                Math.round(maxR / maxPixels),
                Math.round(maxG / maxPixels),
                Math.round(maxB / maxPixels),
                Math.round(maxA / maxPixels));

        boolean splitSucc = !minRGBA.equals(maxRGBA);
        if (splitSucc) {
            centers[minIdx] = minRGBA;
            centers[maxIdx] = maxRGBA;
        }
        return splitSucc;
    }

    /**
     * recompute centers
     *
     * @param colors  distinct colors
     * @param centers centers
     * @param counts  color appear count
     * @param indexes color mapping to indexes
     * @return color center changed counts
     */
    private int refreshCenters(RGBA[] colors, RGBA[] centers, int[] counts, int[] indexes) {
        int changed = 0;
        for (int i = 0; i < centers.length; i++) {
            long R = 0, G = 0, B = 0, A = 0, W = 0;
            // compute center a,r,g,b
            for (int j = 0; j < colors.length; j++) {
                if (indexes[j] != i) continue;
                long count = counts[j];
                R += colors[j].r * count;
                G += colors[j].g * count;
                B += colors[j].b * count;
                A += colors[j].a * count;
                W += count;
            }

            RGBA center;
            if (W == 0) {
                center = randomPickRGBA(colors, counts);
            } else {
                center = new RGBA(
                        Math.round(R / W),
                        Math.round(G / W),
                        Math.round(B / W),
                        Math.round(A / W));
            }

            if (!center.equals(centers[i])) {
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

    private int cluster(RGBA[] colors, RGBA[] centers, int[] indexes) {
        int changed = 0;
        for (int i = 0; i < colors.length; i++) {
            int minDist = Integer.MAX_VALUE;
            int minIdx = 0;

            for (int j = 0; j < centers.length; j++) {
                int dist = colors[i].dist(centers[j]);
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
     * @param colors
     * @param a
     * @param b
     * @return
     */
    private int distance(RGBA[] colors, int a, int b) {
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

    private static class RGBA {
        byte r;
        byte g;
        byte b;
        byte a;
        int hash;

        public RGBA(int r, int g, int b, int a) {
            this.r = (byte) (r & 0xff);
            this.g = (byte) (g & 0xff);
            this.b = (byte) (b & 0xff);
            this.a = (byte) (a & 0xff);
            this.hash = (r & 0xff) << 24 | (g & 0xff) << 16 | (b & 0xff) << 8 | (a & 0xff);
        }

        public RGBA(Color color) {
            this.r = (byte) (color.getRed() & 0xff);
            this.g = (byte) (color.getGreen() & 0xff);
            this.b = (byte) (color.getBlue() & 0xff);
            this.a = (byte) (color.getAlpha() & 0xff);
            this.hash = color.hashCode();
        }

        public Color asColor() {
            return new Color(r & 0xff, g & 0xff, b & 0xff, a & 0xff);
        }

        public int dist(RGBA target) {
            int dist = 0;
            int delta = r - target.r;
            dist += delta * delta;
            delta = g - target.g;
            dist += delta * delta;
            delta = b - target.b;
            dist += delta * delta;
            delta = a - target.a;
            dist += delta * delta;
            return dist;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return (obj != null) && this.hash == ((RGBA) obj).hash;
        }
    }
}