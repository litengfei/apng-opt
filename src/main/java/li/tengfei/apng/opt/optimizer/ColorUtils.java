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
