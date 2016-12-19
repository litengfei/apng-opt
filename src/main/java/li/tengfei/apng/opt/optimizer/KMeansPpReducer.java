package li.tengfei.apng.opt.optimizer;

import java.awt.*;
import java.util.*;


/**
 * K-Means++ Color Reducer
 *
 * @author ltf
 * @since 16/12/14, 上午9:52
 */
public class KMeansPpReducer implements ColorReducer {
    /**
     * reduce color use k-means++ algorithm
     */
    @Override
    public Map<Color, Color> reduce(Color[] pixels, int target) {
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
        int[] indexes = new int[countMap.size()];
        int i = 0;
        for (Map.Entry<Color, Integer> e : countMap.entrySet()) {
            colors[i] = e.getKey();
            counts[i++] = e.getValue();
        }

        Color[] outColors = new Color[target];

        // init centers
        initCenters(pixels, colors, counts, indexes, outColors);

        double avgChanged = -1;
        int iterCount = 0;
        int overAvgCount = 0;

        while (cluster(colors, outColors, indexes) > 0) {
            splitMaxCenters(colors, counts, outColors, indexes, 0.0005f);
            int changed = refreshCenters(colors, outColors, counts, indexes);

            // count continuous cover average times
            if (changed >= avgChanged) overAvgCount++;
            else overAvgCount = 0;
            // if continuous cover average times > N, break iterate
            if (overAvgCount > 20) break;

            // compute average changed times
            if (avgChanged < 0) avgChanged = changed;
            else avgChanged = (avgChanged * iterCount + changed) / (iterCount + 1);
            iterCount++;
        }

        HashMap<Color, Color> mapping = new HashMap<>(colors.length);
        for (int j = 0; j < colors.length; j++) {
            mapping.put(colors[j], outColors[indexes[j]]);
        }
        return mapping;
    }

    /**
     * Random init center points (colors)
     */
    private void initCenters(Color[] pixels, Color[] colors, int[] counts, int[] indexes, Color[] centers) {
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

        cluster(colors, centers, indexes);

        splitMaxCenters(colors, counts, centers, indexes, 0.3f);
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


        for (int maxIndex = 0, minIndex = indexCounts.size() - 1; maxIndex < minIndex; maxIndex++) {
            // split previous max count center, to replace last min count center, until max * splitRate < min
            if (indexCounts.get(maxIndex).count * splitRate < indexCounts.get(minIndex).count)
                break;

            if (splitMaxCenter(colors, counts, centers, indexes, maxIndex, minIndex)) {
                minIndex--;
            }
        }
    }

    /**
     * split MAX CENTER centers[maxIdx] INTO centers[maxIdx], centers[minIdx]
     *
     * @return split success or not
     */
    private boolean splitMaxCenter(Color[] colors, int[] counts, Color[] centers, final int[] indexes, final int maxIdx, int minIdx) {
        int pixels = 0;
        long dist = 0;
        long avgDist = 0;
        double maxR = 0, maxG = 0, maxB = 0, maxA = 0, maxPixels = 0;
        double minR = 0, minG = 0, minB = 0, minA = 0, minPixels = 0;
        Color center = centers[maxIdx];

        // calculate avg ARGB
        for (int i = 0; i < indexes.length; i++) {
            if (indexes[i] != maxIdx) continue;
            dist += distance(center, colors[i]) * counts[i];
            pixels += counts[i];
        }
        avgDist = dist / pixels;

        // calculate avg ARGB
        for (int i = 0; i < indexes.length; i++) {
            if (indexes[i] != maxIdx) continue;
            double count = Math.sqrt(counts[i]);
            if (distance(center, colors[i]) < avgDist) {
                minR += colors[i].getRed() * count;
                minG += colors[i].getGreen() * count;
                minB += colors[i].getBlue() * count;
                minA += colors[i].getAlpha() * count;
                minPixels += count;
            } else {
                maxR += colors[i].getRed() * count;
                maxG += colors[i].getGreen() * count;
                maxB += colors[i].getBlue() * count;
                maxA += colors[i].getAlpha() * count;
                maxPixels += count;
            }
        }

        Color minColor = new Color(
                (int) (minR / minPixels),
                (int) (minG / minPixels),
                (int) (minB / minPixels),
                (int) (minA / minPixels));

        Color maxColor = new Color(
                (int) (maxR / maxPixels),
                (int) (maxG / maxPixels),
                (int) (maxB / maxPixels),
                (int) (maxA / maxPixels));

        boolean splitSucc = !minColor.equals(maxColor);
        if (splitSucc) {
            centers[minIdx] = minColor;
            centers[maxIdx] = maxColor;
        }
        return splitSucc;
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
            double R = 0, G = 0, B = 0, A = 0, W = 0;
            // compute center a,r,g,b
            for (int j = 0; j < colors.length; j++) {
                if (indexes[j] != i) continue;
                double count = Math.sqrt(counts[j]);
                R += colors[j].getRed() * count;
                G += colors[j].getGreen() * count;
                B += colors[j].getBlue() * count;
                A += colors[j].getAlpha() * count;
                W += count;
            }

            Color center = new Color(
                    (int) (R / W),
                    (int) (G / W),
                    (int) (B / W),
                    (int) (A / W));

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