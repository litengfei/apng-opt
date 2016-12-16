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

        while (cluster(colors, outColors, indexes) > 0) {
            int changed = refreshCenters(colors, outColors, counts, indexes);
            if (changed < outColors.length / 10) break;
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

        splitMaxCenters(colors, counts, centers, indexes);
    }

    /**
     * split max center
     */
    private void splitMaxCenters(Color[] colors, int[] counts, Color[] centers, int[] indexes) {
        int[] centerCounts = new int[centers.length];
        for (int i = 0; i < indexes.length; i++) {
            centerCounts[indexes[i]] += counts[i];
        }
        ArrayList<IndexCount> indexCounts = new ArrayList<>(centerCounts.length);
        for (int i = 0; i < centerCounts.length; i++) {
            indexCounts.add(new IndexCount(i, centerCounts[i]));
        }
        Collections.sort(indexCounts);

        for (int i = 0; i < indexCounts.size() / 2; i++) {
            // split previous max count center, to replace last min count center, until max/3 < min
            if (indexCounts.get(i).count / 3 < indexCounts.get(indexCounts.size() - i).count)
                break;

            int maxIndex = indexCounts.get(i).index;
            int minIndex = indexCounts.get(indexCounts.size() - i).index;
            splitMaxCenter(colors, counts, centers, indexes, maxIndex, minIndex);
        }
    }

    /**
     * split MAX CENTER centers[maxIdx] INTO centers[maxIdx], centers[minIdx]
     */
    private void splitMaxCenter(Color[] colors, int[] counts, Color[] centers, final int[] indexes, final int maxIdx, int minIdx) {
        int r = 0, g = 0, b = 0, a = 0, pixels = 0;
        int avgR = 0, avgG = 0, avgB = 0, avgA = 0;
        int maxR = 0, maxG = 0, maxB = 0, maxA = 0;
        int minR = 0, minG = 0, minB = 0, minA = 0;

        // calculate avg ARGB
        for (int i = 0; i < indexes.length; i++) {
            if (indexes[i] != maxIdx) continue;
            r += colors[i].getRed() * counts[i];
            g += colors[i].getGreen() * counts[i];
            b += colors[i].getBlue() * counts[i];
            a += colors[i].getAlpha() * counts[i];
            pixels += counts[i];
        }
        avgR = r / pixels;
        avgG = g / pixels;
        avgB = b / pixels;
        avgA = a / pixels;

        // calculate avg ARGB
        for (int i = 0; i < indexes.length; i++) {
            if (indexes[i] != maxIdx) continue;

            r = colors[i].getRed();
            g = colors[i].getGreen();
            b = colors[i].getBlue();
            a = colors[i].getAlpha();

            if (r < avgR) minR += r * counts[i];
            else maxR += r * counts[i];

            if (g < avgG) minG += g * counts[i];
            else maxG += g * counts[i];

            if (b < avgB) minB += b * counts[i];
            else maxB += b * counts[i];

            if (a < avgA) minA += a * counts[i];
            else maxA += a * counts[i];
        }


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
            int r = 0, g = 0, b = 0, a = 0, pixels = 0;
            // compute center a,r,g,b
            for (int j = 0; j < colors.length; j++) {
                if (indexes[j] == i) {
                    r += colors[j].getRed() * counts[j];
                    g += colors[j].getGreen() * counts[j];
                    b += colors[j].getBlue() * counts[j];
                    a += colors[j].getAlpha() * counts[j];
                    pixels += counts[j];
                }
            }

            Color center = new Color(r / pixels, g / pixels, b / pixels, a / pixels);
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