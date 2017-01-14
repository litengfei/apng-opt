package li.tengfei.apng.opt.optimizer;

import java.awt.*;

/**
 * Common functions for color operating
 *
 * @author ltf
 * @since 17/1/3, 上午10:30
 */
public class ColorUtils {

    /**
     * color distance
     */
    public static int distance(Color a, Color b) {
        int dist = 0;
        int delta = a.getRed() - b.getRed();
        dist += delta * delta * 3;
        delta = a.getGreen() - b.getGreen();
        dist += delta * delta * 3;
        delta = a.getBlue() - b.getBlue();
        dist += delta * delta * 3;
        delta = a.getAlpha() - b.getAlpha();
        dist += delta * delta;
        dist *= 1 + Math.round(Math.sqrt(a.getAlpha() + b.getAlpha()));
        return dist;
    }

    /**
     * convert pixels array to bitmap
     */
    public static Color[][] arrayToMap(Color[] pixels, int height) {
        int width = pixels.length / height;
        Color[][] map = new Color[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                map[y][x] = pixels[y * width + x];
            }
        }
        return map;
    }

    /**
     * convert bitmap to index array
     */
    public static byte[] mapToArray(byte[][] map) {
        if (map.length == 0) return new byte[0];
        byte[] bytes = new byte[map.length * map[0].length];
        int i = 0;
        for (int y = 0; y < map.length; y++) {
            for (int x = 0; x < map[0].length; x++) {
                bytes[i++] = map[y][x];
            }
        }
        return bytes;
    }
}
