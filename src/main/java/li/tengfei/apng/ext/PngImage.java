package li.tengfei.apng.ext;

import java.awt.*;

/**
 * Png Image
 *
 * @author ltf
 * @since 16/12/26, 下午4:02
 */
public class PngImage {
    Color[] pixels;
    int ihdrIndex;
    int plteIndex;
    int trnsIndex;
    int datBeginIndex;
    int datEndIndex;


    public PngImage(Color[] pixels,
                    int ihdrIndex,
                    int plteIndex,
                    int trnsIndex,
                    int datBeginIndex,
                    int datEndIndex) {
        this.pixels = pixels;
        this.ihdrIndex = ihdrIndex;
        this.plteIndex = plteIndex;
        this.trnsIndex = trnsIndex;
        this.datBeginIndex = datBeginIndex;
        this.datEndIndex = datEndIndex;
    }

    public Color[] getPixels() {
        return pixels;
    }

    public int getIhdrIndex() {
        return ihdrIndex;
    }

    public int getPlteIndex() {
        return plteIndex;
    }

    public int getTrnsIndex() {
        return trnsIndex;
    }

    public int getDatBeginIndex() {
        return datBeginIndex;
    }

    public int getDatEndIndex() {
        return datEndIndex;
    }
}
