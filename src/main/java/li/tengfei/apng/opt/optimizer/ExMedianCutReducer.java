package li.tengfei.apng.opt.optimizer;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IllegalFormatFlagsException;
import java.util.Map;

import static li.tengfei.apng.opt.optimizer.ColorUtils.distance;

/**
 * Extended MedianCut Color Reducer with x\y
 *
 * @author ltf
 * @since 16/12/21, 下午6:43
 */
public class ExMedianCutReducer {
    private static final int COLOR_BYTES = 4;
    private Reducer reducer;

    /**
     * Cut Ranking, the biggest one is cut first
     */
    private static long ranking(PColor[] colors) {
        long dists = 0;
        Color center = getMedianColor(colors);
        for (PColor color : colors) {
            dists += Math.sqrt(distance(color, center));
        }
        return (dists);
    }

    /**
     * split colors into two part with median cut
     *
     * @param colors original set
     * @param subs   two sub set
     * @return medianCut success or not
     */
    private static boolean medianCut(PColor[] colors, PColor[][] subs) {
        long[] channelSums = new long[COLOR_BYTES];
        long[] channelDists = new long[COLOR_BYTES];
        double[] channelAvgs = new double[COLOR_BYTES];
        int count = 0;

        // calculate average channel value
        for (PColor color : colors) {
            for (int i = 0; i < COLOR_BYTES; i++) {
                channelSums[i] += getChannelValue(color, i);
            }
            count++;
        }
        for (int i = 0; i < COLOR_BYTES; i++) {
            channelAvgs[i] = (double) channelSums[i] / count;
        }

        // calculate channel distances
        for (PColor color : colors) {
            for (int i = 0; i < COLOR_BYTES; i++) {
                channelDists[i] += Math.round(Math.abs((getChannelValue(color, i) - channelAvgs[i])));
            }
        }

        // select cut channel
        int cutChannel = 0;
        double cutAvg;
        long maxDist = 0;
        for (int i = 0; i < COLOR_BYTES; i++) {
            if (maxDist < channelDists[i]) {
                maxDist = channelDists[i];
                cutChannel = i;
            }
        }
        cutAvg = channelAvgs[cutChannel];

        // median cut colors
        ArrayList<PColor> subA = new ArrayList<>();
        ArrayList<PColor> subB = new ArrayList<>();
        ArrayList<PColor> subE = new ArrayList<>();
        for (PColor color : colors) {
            int channelValue = getChannelValue(color, cutChannel);
            if (channelValue < cutAvg) subA.add(color);
            else if (channelValue > cutAvg) subB.add(color);
            else subE.add(color);
        }
        if (subA.size() <= subB.size()) subA.addAll(subE);
        else subB.addAll(subE);

        if (subA.size() == 0 || subB.size() == 0) return false;


        subs[0] = new PColor[subA.size()];
        subs[1] = new PColor[subB.size()];
        subA.toArray(subs[0]);
        subB.toArray(subs[1]);
        return true;
    }


    /**
     * get MedianColor of the colors
     */
    private static Color getMedianColor(PColor[] colors) {
        long R = 0, G = 0, B = 0, A = 0, W = 0;
        // compute center a,r,g,b
        for (PColor color : colors) {
            R += color.getRed();
            G += color.getGreen();
            B += color.getBlue();
            A += color.getAlpha();
            W++;
        }

        Color center = new Color(
                Math.round(R / W),
                Math.round(G / W),
                Math.round(B / W),
                Math.round(A / W));
        return center;
    }

    /**
     * get color value by channel index r0 g1 b2 a3
     */
    private static int getChannelValue(Color color, int index) {
        switch (index) {
            case 0:
                return color.getRed();
            case 1:
                return color.getGreen();
            case 2:
                return color.getBlue();
            default:
                return color.getAlpha();
        }
    }

    public Mapping reduce(Color[][] pixels, int target) {
        if (pixels.length == 0) throw new IllegalFormatFlagsException("not a valid image: 0 row pixels");
        // count color appearance
        HashMap<Color, Integer> countMap = new HashMap<Color, Integer>();
        for (Color[] ps : pixels) {
            for (Color p : ps) {
                if (countMap.containsKey(p)) {
                    countMap.put(p, countMap.get(p) + 1);
                } else {
                    countMap.put(p, 1);
                }
            }
        }

        // return if not need reduce
        if (countMap.size() <= target) {
            Mapping mapping = new Mapping();
            mapping.colorTable = new Color[countMap.size()];
            mapping.image = new byte[pixels.length][pixels[0].length];
            Map<Color, Byte> colorByteMap = new HashMap<>(countMap.size());
            int i = 0;
            for (Color color : countMap.keySet()) {
                mapping.colorTable[i] = color;
                colorByteMap.put(color, (byte) (i & 0xff));
                i++;
            }
            for (int y = 0; y < pixels.length; y++) {
                for (int x = 0; x < pixels[0].length; x++) {
                    mapping.image[y][x] = colorByteMap.get(pixels[y][x]);
                }
            }
            return mapping;
        }

        PColor[] colors = new PColor[pixels.length * pixels[0].length];
        int i = 0;
        for (int y = 0; y < pixels.length; y++) {
            for (int x = 0; x < pixels[0].length; x++) {
                colors[i++] = new PColor(pixels[y][x], y, x);
            }
        }

        Reducer reducer = new Reducer(colors);
        while (reducer.count < target && reducer.split()) ;


        return reducer.getMapping();
    }

    private static class PColor extends Color {
        final int y;
        final int x;

        PColor(Color color, int y, int x) {
            super(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
            this.x = x;
            this.y = y;
        }
    }

    private static class Reducer {
        private Node head;
        private int count;

        Reducer(PColor[] colors) {
            add(new Node(colors));
        }

        /**
         * add colors into linked list just before the one who's rank is little than this
         */
        private void add(Node node) {
            node.pre = null;
            node.next = null;
            if (head == null) {
                head = node;
            } else {
                Node next = head;
                // add new node before the one who's rank is little than this
                for (; ; ) {
                    if (next.rank < node.rank) {
                        node.pre = next.pre;
                        node.next = next;
                        next.pre = node;
                        if (node.pre == null) {
                            head = node;
                        } else {
                            node.pre.next = node;
                        }
                        break;
                    }

                    if (next.next == null) {
                        // no next node, put the new one at end
                        next.next = node;
                        node.pre = next;
                        break;
                    } else {
                        // move to next node
                        next = next.next;
                    }
                }
            }
            count++;
        }

        /**
         * split the highest ranking node into two
         *
         * @return split success or not
         */
        boolean split() {
            PColor[][] subColors = new PColor[2][];
            boolean succ = false;
            for (; ; ) {
                Node node = poll();
                if (node == null || node.rank == Node.CANT_CUT_RANK) {
                    break;
                }
                succ = medianCut(node.colors, subColors);
                if (succ) {
                    add(new Node(subColors[0]));
                    add(new Node(subColors[1]));
                    break;
                } else {
                    node.setCantCut();
                    add(node);
                }
            }
            return succ;
        }

        /**
         * get color mappings
         */
        Mapping getMapping() {
            Node next = head;
            int count = 0, x = 0, y = 0;
            while (next != null) {
                for (PColor c : next.colors) {
                    x = x < c.x ? c.x : x;
                    y = y < c.y ? c.y : y;
                }
                count++;
                next = next.next;
            }
            Mapping mapping = new Mapping();
            mapping.colorTable = new Color[count];
            mapping.image = new byte[++y][++x];

            next = head;
            int i = 0;
            while (next != null) {
                mapping.colorTable[i] = getMedianColor(next.colors);
                for (PColor c : next.colors) mapping.image[c.y][c.x] = (byte) (i & 0xff);
                i++;
                next = next.next;
            }
            return mapping;
        }

        /**
         * poll head node
         */
        private Node poll() {
            Node node = head;
            if (head != null) {
                head = head.next;
                if (head != null) head.pre = null;
            }
            count--;
            return node;
        }

        /**
         * 2-way chain Node contains colors and rank
         */
        private static class Node {
            static final int CANT_CUT_RANK = Integer.MIN_VALUE;
            final PColor[] colors;
            long rank;
            Node next;
            Node pre;

            Node(PColor[] colors) {
                this.colors = colors;
                rank = ranking(colors);
            }

            private void setCantCut() {
                rank = CANT_CUT_RANK;
            }
        }
    }

    public static class Mapping {
        public Color[] colorTable;
        public byte[][] image;
    }
}
