package li.tengfei.apng.opt.optimizer;

import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IllegalFormatFlagsException;
import java.util.Map;

/**
 * @author ltf
 * @since 17/1/3, 上午11:25
 */
public class BaseColorMapper implements ColorMapper {

    /**
     * generate indexed image
     *
     * @param pixels       original image pixels in Color
     * @param height       image height
     * @param colorMap     colorMap
     * @param colorIndex   color index of color table
     * @param pixelIndexes output indexed image, pixelIndexes.length = pixels.length
     */
    protected void genIndexedImage(Color[] pixels, int height,
                                   Map<Color, Color> colorMap, HashMap<Color, Integer> colorIndex,
                                   byte[] pixelIndexes) {
        for (int i = 0; i < pixels.length; i++)
            pixelIndexes[i] = (byte) (colorIndex.get(colorMap.get(pixels[i])) & 0xff);
    }

    /**
     * prepare color table
     */
    protected Color[] prepareColorTable(Map<Color, Color> colorMap) {
        HashSet<Color> set = new HashSet<>(256);
        set.addAll(colorMap.values());
        if (set.size() > 256)
            throw new IllegalFormatFlagsException("indexed png not supported BitDepth=" + set.size());
        Color[] colorTable = new Color[set.size()];
        set.toArray(colorTable);

        // optimize color table: move all opaque colors to end to save trns size
        int pre = 0, end = colorTable.length - 1;
        while (pre < end) {
            if (colorTable[end].getAlpha() == 255) {
                end--;
                continue;
            }
            if (colorTable[pre].getAlpha() != 255) {
                pre++;
                continue;
            }
            Color color = colorTable[end];
            colorTable[end] = colorTable[pre];
            colorTable[pre] = color;
            end--;
            pre++;
        }
        return colorTable;
    }

    @Override
    public final Mapping mapping(Color[] pixels, int height, Map<Color, Color> colorMap) {
        Mapping mapping = new Mapping();
        mapping.colorTable = prepareColorTable(colorMap);
        mapping.pixelIndexes = new byte[pixels.length];

        // compute color index in color table
        HashMap<Color, Integer> colorIndex = new HashMap<>(mapping.colorTable.length);
        for (int i = 0; i < mapping.colorTable.length; i++) {
            colorIndex.put(mapping.colorTable[i], i);
        }

        genIndexedImage(pixels, height,
                colorMap, colorIndex,
                mapping.pixelIndexes);

        return mapping;
    }
}
