package li.tengfei.apng.opt.optimizer;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * K-Means++ Color Reducer
 *
 * @author ltf
 * @since 16/12/14, 上午9:52
 */
public class KMeansPpReducer implements ColorReducer {

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


        return null;
    }

    /**
     * init center points (colors)
     */
    private int initCenters(Color[] colors, Color[] centers, int[] counts, int[] indexes) {
        Random rand = new Random();
        // random first center
        centers[0] = pixels[rand.nextInt(pixels.length)];
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


//    /**
//     * return pixels count of all colors
//     */
//    private int pixelCount(Map<Color, Integer> colors) {
//        int count = 0;
//        for (int i : colors.values()) count += i;
//        return count;
//    }

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
}