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

        Color[] colors = new Color[countMap.size()];
        int[] counts = new int[countMap.size()];
        int i = 0;
        for (Map.Entry<Color, Integer> e : countMap.entrySet()) {
            colors[i] = e.getKey();
            counts[i++] = e.getValue();
        }
        int[] indexes = new int[countMap.size()];
        Color[] outColors = new Color[target];

        reduce(colors, counts, indexes, outColors);

        HashSet<Color> distinctOut = new HashSet<>();
        distinctOut.addAll(Arrays.asList(outColors));
        log.debug("finally output colors count: " + distinctOut.size());

        HashMap<Color, Color> mapping = new HashMap<>(colors.length);
        for (int j = 0; j < colors.length; j++) {
            mapping.put(colors[j], outColors[indexes[j]]);
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
        // init centers
        initCenters(colors, counts, indexes, centers);

        int lastMinChanged = Integer.MAX_VALUE;
        int countOverLastMin = 0;
        while (cluster(colors, centers, indexes) > 0) {
            //splitMaxCenters(colors, counts, centers, indexes, 0.000005f);
            int changed = refreshCenters(colors, centers, counts, indexes);

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
    private void initCenters(Color[] colors, int[] counts, int[] indexes, Color[] centers) {
        int pixels = 0;
        // random init centers
        for (int i = 0; i < centers.length; i++) {
            Color candidate = null;
            while (candidate == null) {

                candidate = randomPickColor(colors, counts);
                // remove exists color
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
        long R = 0, G = 0, B = 0, A = 0, pixels = 0;

        // calculate avg ARGB
        for (int i = 0; i < indexes.length; i++) {
            if (indexes[i] != maxIdx) continue;
            long count = counts[i];
            R += colors[i].getRed() * count;
            G += colors[i].getGreen() * count;
            B += colors[i].getBlue() * count;
            A += colors[i].getAlpha() * count;
            pixels += count;
        }

        if (pixels == 0) return false;

        double avgR, avgG, avgB, avgA;
        double disR, disG, disB, disA;
        double absR, absG, absB, absA;
        long maxR = 0, maxG = 0, maxB = 0, maxA = 0, maxPixels = 0;
        long minR = 0, minG = 0, minB = 0, minA = 0, minPixels = 0;
        avgR = R / pixels;
        avgG = G / pixels;
        avgB = B / pixels;
        avgA = A / pixels;
        // calculate avg ARGB
        for (int i = 0; i < indexes.length; i++) {
            if (indexes[i] != maxIdx) continue;
            R = colors[i].getRed();
            G = colors[i].getGreen();
            B = colors[i].getBlue();
            A = colors[i].getAlpha();

            disR = avgR - R;
            disG = avgG - G;
            disB = avgB - B;
            disA = avgA - A;
            absR = Math.abs(disR);
            absG = Math.abs(disG);
            absB = Math.abs(disB);
            absA = Math.abs(disA);

            double maxAbs = absR;
            boolean isMax = absR == disR;

            if (maxAbs < absG) {
                maxAbs = absG;
                isMax = absG == disG;
            }
            if (maxAbs < absB) {
                maxAbs = absB;
                isMax = absB == disB;
            }
            if (maxAbs < absA) {
                isMax = absA == disA;
            }

            if (isMax) {
                maxR += R;
                maxG += G;
                maxB += B;
                maxA += A;
                maxPixels += counts[i];
                indexes[i] = maxIdx;
            } else {
                minR += R;
                minG += G;
                minB += B;
                minA += A;
                minPixels += counts[i];
                indexes[i] = minIdx;
            }
        }

        Color minColor = new Color(
                Math.round(minR / minPixels),
                Math.round(minG / minPixels),
                Math.round(minB / minPixels),
                Math.round(minA / minPixels));

        Color maxColor = new Color(
                Math.round(maxR / maxPixels),
                Math.round(maxG / maxPixels),
                Math.round(maxB / maxPixels),
                Math.round(maxA / maxPixels));

        boolean splitSucc = !minColor.equals(maxColor);
        if (splitSucc) {
            centers[minIdx] = minColor;
            centers[maxIdx] = maxColor;
        }
        return splitSucc;
    }

    /**
     * | sumMin0 | countMin0 | sumMax0 | countMax0 | sumMin1 | countMin1 | sumMax1 | countMax1 | ...
     */
    private void countColor() {


    }

    /**
     * KMeans++ init center points (colors)
     */
    private void initCentersKMeanspp(Color[] pixels, Color[] centers) {
        Random rand = new Random();
        // random init centers
        for (int i = 0; i < centers.length; i++) {
            Color candidate = null;
            while (candidate == null) {
                candidate = pixels[rand.nextInt(pixels.length)];
                // remove exists color
                for (int j = 0; j < i; j++) {
                    if (candidate.equals(centers[i])) candidate = null;
                }
            }
            centers[i] = candidate;
        }

        // compute dx sum
        int sumDx = 0;
        int[] dxs = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int dx = Integer.MAX_VALUE;
            for (Color c : centers) {
                int d = distance(c, pixels[i]);
                dx = dx > d ? d : dx;
            }
            dxs[i] = dx;
            sumDx += dx;
        }

        // reInit centers
        for (int i = 0; i < centers.length; i++) {
            int sdx = sumDx;
            Color candidate = null;
            while (candidate == null) {
                int r = rand.nextInt(pixels.length);
                candidate = pixels[r];
                sdx -= dxs[r];
                if (sdx > 0) {
                    candidate = null;
                    continue;
                }

                // remove exists color
                for (int j = 0; j < i; j++) {
                    if (candidate.equals(centers[i])) candidate = null;
                }
            }
            centers[i] = candidate;
        }
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
            long R = 0, G = 0, B = 0, A = 0, W = 0;
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

            Color center;
            if (W == 0) {
                center = randomPickColor(colors, counts);
            } else {
                center = new Color(
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

    /**
     * calculate distance of two color
     */
    private int distance(Color a, Color b) {
        int dist = 0;
        int delta = a.getRed() - b.getRed();
        dist += delta * delta;
        delta = a.getGreen() - b.getGreen();
        dist += delta * delta;
        delta = a.getBlue() - b.getBlue();
        dist += delta * delta;
        delta = a.getAlpha() - b.getAlpha();
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
}