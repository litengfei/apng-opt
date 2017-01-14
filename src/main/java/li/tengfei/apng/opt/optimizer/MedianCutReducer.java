package li.tengfei.apng.opt.optimizer;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static li.tengfei.apng.opt.optimizer.ColorUtils.distance;

/**
 * MedianCut color Reducer
 *
 * @author ltf
 * @since 16/12/21, 下午6:43
 */
public class MedianCutReducer implements ColorReducer {
    private static final int COLOR_BYTES = 4;
    private Reducer reducer;

    /**
     * Cut Ranking, the biggest one is cut first
     */
    private static long ranking(NColor[] colors) {
        double dists = 0;
        Color center = getMedianColor(colors);
        for (NColor color : colors) {
            dists += Math.sqrt(distance(color, center)) * color.count;
        }
        return (long)dists;
    }

    /**
     * split colors into two part with median cut
     *
     * @param colors original set
     * @param subs   two sub set
     * @return medianCut success or not
     */
    private static boolean medianCut(NColor[] colors, NColor[][] subs) {
        long[] channelSums = new long[COLOR_BYTES];
        long[] channelDists = new long[COLOR_BYTES];
        double[] channelAvgs = new double[COLOR_BYTES];
        int count = 0;

        // calculate average channel value
        for (NColor color : colors) {
            for (int i = 0; i < COLOR_BYTES; i++) {
                channelSums[i] += getChannelValue(color, i) * color.count;
            }
            count += color.count;
        }
        for (int i = 0; i < COLOR_BYTES; i++) {
            channelAvgs[i] = (double) channelSums[i] / count;
        }

        // calculate channel distances
        for (NColor color : colors) {
            for (int i = 0; i < COLOR_BYTES; i++) {
                channelDists[i] += Math.round(Math.abs((getChannelValue(color, i) - channelAvgs[i])) * color.count);
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
        ArrayList<NColor> subA = new ArrayList<>();
        ArrayList<NColor> subB = new ArrayList<>();
        ArrayList<NColor> subE = new ArrayList<>();
        for (NColor color : colors) {
            int channelValue = getChannelValue(color, cutChannel);
            if (channelValue < cutAvg) subA.add(color);
            else if (channelValue > cutAvg) subB.add(color);
            else subE.add(color);
        }
        if (subA.size() <= subB.size()) subA.addAll(subE);
        else subB.addAll(subE);

        if (subA.size() == 0 || subB.size() == 0) return false;


        subs[0] = new NColor[subA.size()];
        subs[1] = new NColor[subB.size()];
        subA.toArray(subs[0]);
        subB.toArray(subs[1]);
        return true;
    }


    /**
     * get MedianColor of the colors
     */
    private static Color getMedianColor(NColor[] colors) {
        long R = 0, G = 0, B = 0, A = 0, W = 0;
        // compute center a,r,g,b
        for (NColor color : colors) {
            R += color.getRed() * color.count;
            G += color.getGreen() * color.count;
            B += color.getBlue() * color.count;
            A += color.getAlpha() * color.count;
            W += color.count;
        }

        Color vCenter = new Color(
                Math.round(R / W),
                Math.round(G / W),
                Math.round(B / W),
                Math.round(A / W));
        return vCenter;
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

    @Override
    public Map<Color, Color> reduce(Color[] pixels, int target) {
        HashMap<Color, Integer> countMap = new HashMap<Color, Integer>();
        for (Color p : pixels) {
            if (countMap.containsKey(p)) {
                countMap.put(p, countMap.get(p) + 1);
            } else {
                countMap.put(p, 1);
            }
        }

        // return if not need reduce
        if (countMap.size() <= target) {
            HashMap<Color, Color> mapping = new HashMap<>(countMap.size());
            for (Color color : countMap.keySet()) {
                mapping.put(color, color);
            }
            return mapping;
        }

        NColor[] colors = new NColor[countMap.size()];
        int i = 0;
        for (Color color : countMap.keySet()) {
            colors[i++] = new NColor(color, countMap.get(color));
        }

        Reducer reducer = new Reducer(colors);
        while (reducer.count < target && reducer.split()) ;
        return reducer.getMapping();
    }

    private static class NColor extends Color {
        final int count;

        NColor(Color color, int count) {
            super(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
            this.count = count;
        }
    }

    private static class Reducer {
        private Node head;
        private int count;

        Reducer(NColor[] colors) {
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
            NColor[][] subColors = new NColor[2][];
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
        Map<Color, Color> getMapping() {
            HashMap<Color, Color> map = new HashMap<>();
            Node next = head;
            while (next != null) {
                Color median = getMedianColor(next.colors);
                for (NColor color : next.colors) map.put(color, median);
                next = next.next;
            }
            return map;
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
            final NColor[] colors;
            long rank;
            Node next;
            Node pre;

            Node(NColor[] colors) {
                this.colors = colors;
                rank = ranking(colors);
            }

            private void setCantCut() {
                rank = CANT_CUT_RANK;
            }
        }
    }
}
