package li.tengfei.apng.ext;

import static java.lang.Math.abs;

/**
 * Png Filters in filter method 0
 *
 * @author ltf
 * @since 16/12/29, 下午2:26
 */
public class PngFilters {
    public static final Filter[] filters = {
            new Filter(),
            new FilterSub(),
            new FilterUp(),
            new FilterAverage(),
            new FilterPaeth()
    };

    /**
     * filter operate on a row
     *
     * @param in         input bytes
     * @param out        output bytes
     * @param pos        current scan-line start position (include filter byte)
     * @param rowBytes   bytes count in one pixel scan-line (include one filter byte)
     * @param pixelBytes bytes count in one pixel (>=1)
     */
    public static void filt(byte[] in, byte[] out,
                            int pos, int rowBytes, int pixelBytes) {
        filters[out[pos]].filt(in, out, pos, rowBytes, pixelBytes);
    }

    /**
     * Reconstruct operate on a row
     *
     * @param in         input bytes
     * @param out        output bytes
     * @param pos        current scan-line start position (include filter byte)
     * @param rowBytes   bytes count in one pixel scan-line (include one filter byte)
     * @param pixelBytes bytes count in one pixel (>=1)
     */
    public static void recon(byte[] in, byte[] out,
                             int pos, int rowBytes, int pixelBytes) {
        filters[in[pos]].recon(in, out, pos, rowBytes, pixelBytes);
    }

    static class Filter {
        void filt(byte[] in, byte[] out,
                  int pos, final int rowBytes, final int pixelBytes) {
            boolean firstScanLine = pos < rowBytes;
            if (firstScanLine) {
                for (int i = 0; i < pixelBytes; i++) {
                    pos++;
                    out[pos] = filt(in[pos], (byte) 0, (byte) 0, (byte) 0);
                }
                for (int i = pixelBytes + 1; i < rowBytes; i++) {
                    pos++;
                    out[pos] = filt(in[pos], in[pos - pixelBytes], (byte) 0, (byte) 0);
                }
            } else {
                for (int i = 0; i < pixelBytes; i++) {
                    pos++;
                    out[pos] = filt(in[pos], (byte) 0, in[pos - rowBytes], (byte) 0);
                }
                for (int i = pixelBytes + 1; i < rowBytes; i++) {
                    pos++;
                    out[pos] = filt(in[pos], in[pos - pixelBytes], in[pos - rowBytes], in[pos - rowBytes - pixelBytes]);
                }
            }
        }

        void recon(byte[] in, byte[] out,
                   int pos, final int rowBytes, final int pixelBytes) {
            boolean firstScanLine = pos < rowBytes;
            if (firstScanLine) {
                for (int i = 0; i < pixelBytes; i++) {
                    pos++;
                    out[pos] = recon(in[pos], (byte) 0, (byte) 0, (byte) 0);
                }
                for (int i = pixelBytes + 1; i < rowBytes; i++) {
                    pos++;
                    out[pos] = recon(in[pos], out[pos - pixelBytes], (byte) 0, (byte) 0);
                }
            } else {
                for (int i = 0; i < pixelBytes; i++) {
                    pos++;
                    out[pos] = recon(in[pos], (byte) 0, out[pos - rowBytes], (byte) 0);
                }
                for (int i = pixelBytes + 1; i < rowBytes; i++) {
                    pos++;
                    out[pos] = recon(in[pos], out[pos - pixelBytes], out[pos - rowBytes], out[pos - rowBytes - pixelBytes]);
                }
            }
        }

        byte filt(byte x, byte a, byte b, byte c) {
            return x;
        }

        byte recon(byte x, byte a, byte b, byte c) {
            return x;
        }
    }

    private static class FilterSub extends Filter {
        byte filt(byte x, byte a, byte b, byte c) {
            return (byte) (x - a);
        }

        byte recon(byte x, byte a, byte b, byte c) {
            return (byte) (x + a);
        }
    }

    private static class FilterUp extends Filter {
        byte filt(byte x, byte a, byte b, byte c) {
            return (byte) (x - b);
        }

        byte recon(byte x, byte a, byte b, byte c) {
            return (byte) (x + b);
        }
    }

    private static class FilterAverage extends Filter {
        byte filt(byte x, byte a, byte b, byte c) {
            return (byte) (x - (byte) (((a & 0xff) + (b & 0xff)) >> 1));
        }

        byte recon(byte x, byte a, byte b, byte c) {
            return (byte) (x + (byte) (((a & 0xff) + (b & 0xff)) >> 1));
        }
    }

    private static class FilterPaeth extends Filter {
        byte filt(byte x, byte a, byte b, byte c) {
            return (byte) (x - (byte) paeth(a & 0xff, b & 0xff, c & 0xff));
        }

        byte recon(byte x, byte a, byte b, byte c) {
            return (byte) (x + (byte) paeth(a & 0xff, b & 0xff, c & 0xff));
        }

        int paeth(int a, int b, int c) {
            int p = a + b - c;
            int pa = abs(p - a);
            int pb = abs(p - b);
            int pc = abs(p - c);
            if (pa <= pb & pa <= pc) return a;
            else if (pb <= pc) return b;
            else return c;
        }
    }
}
