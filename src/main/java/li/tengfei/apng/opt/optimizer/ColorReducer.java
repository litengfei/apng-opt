package li.tengfei.apng.opt.optimizer;

import java.awt.*;

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
     * @param pixels     all pixels' color
     * @param target     target color max count, -1 means auto detect
     * @param imageWidth image width
     * @return [0][] color reduced pixels, [1][] color table
     */
    Color[][] reduce(Color[] pixels, int target, int imageWidth);
}



