package li.tengfei.apng.opt.optimizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;

import static li.tengfei.apng.opt.optimizer.ColorUtils.distance;


/**
 * K-Means Color Reducer
 *
 * @author ltf
 * @since 16/12/14, 上午9:52
 */
public class KMeansReducer implements ColorReducer {
    private static final Logger log = LoggerFactory.getLogger(KMeansReducer.class);
    private static final int MAX_CACHE_DISTS = 1024 * 10; // 200M
    private Random rand = new Random();
    private int mPixels;
    private ColorReducer initReducer;

    public ColorReducer getInitReducer() {
        return initReducer;
    }

    public void setInitReducer(ColorReducer initReducer) {
        this.initReducer = initReducer;
    }

    /**
     * reduce color use k-means++ algorithm
     */
    @Override
    public Map<Color, Color> reduce(Color[] pixels, int target) {
        // count color appearance
        mPixels = pixels.length;
        HashMap<Color, Integer> countMap = new HashMap<Color, Integer>();
        for (Color p : pixels) {
            if (countMap.containsKey(p)) {
                countMap.put(p, countMap.get(p) + 1);
            } else {
                countMap.put(p, 1);
            }
        }

        // return if not need reduce
        if (countMap.size() <= target) {
            HashMap<Color, Color> mapping = new HashMap<>(countMap.size());
            for (Color color : countMap.keySet()) {
                mapping.put(color, color);
            }
            return mapping;
        }

        // set colors [ sorted by count ]
        ArrayList<ColorCount> colorCounts = new ArrayList<>(countMap.size());
        Color[] colors = new Color[countMap.size()];
        int[] counts = new int[countMap.size()];
        for (Map.Entry<Color, Integer> e : countMap.entrySet()) {
            colorCounts.add(new ColorCount(e.getKey(), e.getValue()));
        }
        Collections.sort(colorCounts);
        for (int i = 0; i < colorCounts.size(); i++) {
            colors[i] = colorCounts.get(i).color;
            counts[i] = colorCounts.get(i).count;
        }

        int[] indexes = new int[countMap.size()];
        Color[] centers = new Color[target];


        // init centers
        if (initReducer == null) {
            // use inner center init method
            initCenters(colors, counts, indexes, centers);
        } else {
            // use out center init method
            Map<Color, Color> initMapping = initReducer.reduce(pixels, target);
            Set<Color> cs = new HashSet<>(target);
            for (Color c : initMapping.values()) cs.add(c);
            int i = 0;
            for (Color c : cs) centers[i++] = c;
            while (i < target) centers[i++] = randomPickColor(colors, counts);
        }

        reduce(colors, counts, indexes, centers);

        HashSet<Color> distinctOut = new HashSet<>();
        distinctOut.addAll(Arrays.asList(centers));
        log.debug("finally output colors count: " + distinctOut.size());

        HashMap<Color, Color> mapping = new HashMap<>(colors.length);
        for (int j = 0; j < colors.length; j++) {
            mapping.put(colors[j], centers[indexes[j]]);
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
    private void reduce(Color[] colors, int[] counts, int[] indexes, Color[] centers) {
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
            if (countOverLastMin > 50 && changed <= lastMinChanged) {
                cluster(colors, centers, indexes);
                break;
            }

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
    private void initCenters(Color[] colors, int[] counts, int[] indexes, Color[] centers) {
        int pixels = 0;
        // random init centers
        for (int i = 0; i < centers.length; i++) {
            Color candidate = null;
            while (candidate == null) {
                candidate = randomPickColor(colors, counts);
                // remove exists Color
                for (int j = 0; j < i; j++) {
                    if (candidate.equals(centers[i])) candidate = null;
                }
            }
            centers[i] = candidate;
        }

        cluster(colors, centers, indexes);

        splitMaxCenters(colors, counts, centers, indexes, 0.033f);
    }

    private Color randomPickColor(Color[] colors, int[] counts) {
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
    private void splitMaxCenters(Color[] colors, int[] counts, Color[] centers, int[] indexes, float splitRate) {
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
    private boolean splitMaxCenter(Color[] colors, int[] counts, Color[] centers, final int[] indexes, final int maxIdx, int minIdx) {
        // generate sub colors/counts/centers/indexes for split
        int count = 0;
        for (int idx : indexes) if (idx == maxIdx) count++;
        if (count < 2) return false; // appears when only one color counts all as a center
        Color[] subColors = new Color[count];
        int[] subCounts = new int[count];
        int[] subIndexes = new int[count];
        Color[] subCenters = new Color[2];
        int idx = 0;
        for (int i = 0; i < indexes.length; i++) {
            if (indexes[i] != maxIdx) continue;
            subColors[idx] = colors[i];
            subCounts[idx++] = counts[i];
        }

        // get the longest distance two color
        int maxDist = 0, maxX = 0, maxY = 0;
        for (int x = 0; x < subColors.length; x++) {
            for (int y = x + 1; y < subColors.length; y++) {
                int dist = distance(subColors[x], subColors[y]);
                if (dist > maxDist) {
                    maxDist = dist;
                    maxX = x;
                    maxY = y;
                }
            }
        }
        // use the longest distance color as two center
        subCenters[0] = subColors[maxX];
        subCenters[1] = subColors[maxY];

        // do cluster and refresh centers
        cluster(subColors, subCenters, subIndexes);
        refreshCenters(subColors, subCenters, subCounts, subIndexes);

        boolean splitSucc = !subCenters[0].equals(subCenters[1]);
        if (splitSucc) {
            centers[minIdx] = subCenters[0];
            centers[maxIdx] = subCenters[1];
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
    private int refreshCenters(Color[] colors, Color[] centers, int[] counts, int[] indexes) {
        int changed = 0;
        for (int i = 0; i < centers.length; i++) {
            double R = 0, G = 0, B = 0, A = 0, W = 0;
            // compute center a,r,g,b
            for (int j = 0; j < colors.length; j++) {
                if (indexes[j] != i) continue;
                long count = counts[j];
                R += colors[j].getRed() * count;
                G += colors[j].getGreen() * count;
                B += colors[j].getBlue() * count;
                A += colors[j].getAlpha() * count;
                W += count;
            }

            Color vCenter = new Color(
                    (int) Math.round(R / W),
                    (int) Math.round(G / W),
                    (int) Math.round(B / W),
                    (int) Math.round(A / W));

            Color center = centers[i];
            int minDist = Integer.MAX_VALUE;
            for (int j = 0; j < colors.length; j++) {
                if (indexes[j] != i) continue;
                int dist = distance(vCenter, colors[j]);
                if (dist < minDist) {
                    minDist = dist;
                    center = colors[j];
                }
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

    private int cluster(Color[] colors, Color[] centers, int[] indexes) {
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