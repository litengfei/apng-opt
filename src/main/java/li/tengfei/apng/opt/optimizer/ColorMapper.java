package li.tengfei.apng.opt.optimizer;

import java.awt.*;
import java.util.Map;

/**
 * mapping color to indexed color
 *
 * @author ltf
 * @since 17/1/3, 上午11:03
 */
public interface ColorMapper {
    /**
     * mapping color to indexes
     *
     * @param pixels   image pixels in Color Mode
     * @param height   image height (scan-lines count)
     * @param colorMap color mapping
     * @return Color-Table & image indexed pixels
     */
    Mapping mapping(Color[] pixels, int height, Map<Color, Color> colorMap);

    class Mapping {
        public Color[] colorTable;
        public byte[] pixelIndexes;
    }
}
