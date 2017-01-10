package li.tengfei.apng.opt.optimizer;

import java.awt.*;

/**
 * Pixel, Extend Color
 *
 * @author ltf
 * @since 17/1/9, 下午6:22
 */
public class Pixel extends Color {

    int x;
    int y;
    int z;


    public Pixel(int r, int g, int b) {
        super(r, g, b);
    }

    public Pixel(int r, int g, int b, int a) {
        super(r, g, b, a);
    }

    public Pixel(int r, int g, int b, int a,
                 int x, int y, int z) {
        this(r, g, b, a);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ ((x & 0xfff) | (y & 0xfff) << 12 | z << 24);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Pixel && ((Pixel) obj).getRGB() == this.getRGB()
                && ((Pixel) obj).x == this.x
                && ((Pixel) obj).y == this.y
                && ((Pixel) obj).z == this.z;
    }
}
