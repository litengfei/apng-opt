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
     * @param pixels all pixels' color
     * @param target target color max count, -1 means auto detect
     * @return color reduce mapping
     */
    Map<Color, Color> reduce(Color[] pixels, int target);
}



