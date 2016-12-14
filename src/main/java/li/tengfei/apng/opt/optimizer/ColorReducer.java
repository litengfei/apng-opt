package li.tengfei.apng.opt.optimizer;

import java.awt.*;
import java.util.Map;

/**
 * Color Reducer
 *
 * @author ltf
 * @since 16/12/13, 下午7:37
 */
public interface ColorReducer {
    /**
     * reduce colors
     *
     * @param colors colors and appear count
     * @param target target color count
     * @return color reduce mapping
     */
    Map<Color, Color> reduce(Map<Color, Integer> colors, int target);
}
