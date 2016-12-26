package li.tengfei.apng.opt.optimizer;

import li.tengfei.apng.base.ApngIHDRChunk;
import li.tengfei.apng.ext.ByteArrayPngChunk;
import li.tengfei.apng.opt.builder.AngChunkData;
import li.tengfei.apng.opt.builder.AngData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IllegalFormatFlagsException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import static li.tengfei.apng.base.ApngConst.*;

/**
 * Optimizer for the ANG frames palette
 *
 * @author ltf
 * @since 16/12/12, 下午2:05
 */
public class PaletteOptimizer implements AngOptimizer {

    private static final int COLOR_B = 0x1;
    private static final int COLOR_G = 0x100;
    private static final int COLOR_R = 0x10000;
    private static final Logger log = LoggerFactory.getLogger(PaletteOptimizer.class);

    @Override
    public AngData optimize(AngData ang) {
        try {
            colorReduce(ang);
        } catch (DataFormatException e) {
            e.printStackTrace();
        }
        //ArrayList<Palette> palettes = genPalettes(ang);
        //palettes = optmizePalette(palettes);
        return null;
    }

    /**
     * reduce colors
     */
    private void colorReduce(AngData ang) throws DataFormatException {
        int chunkIndex = -1;
        int ihdrIndex = -1;
        int plteIndex = -1;
        int trnsIndex = -1;
        int allCount = 0;
        ArrayList<FrameImage> frameImages = new ArrayList<>();
        ApngIHDRChunk ihdr = new ApngIHDRChunk();
        for (AngChunkData chunk : ang.getChunks()) {
            chunkIndex++;
            switch (chunk.getTypeCode()) {
                case CODE_IHDR:
                    ihdr.parse(new ByteArrayPngChunk(chunk.getData()));
                    if (ihdr.getColorType() != 3)
                        throw new IllegalFormatFlagsException("colorType=" + ihdr.getColorType());
                    if (ihdr.getFilterMethod() != 0)
                        throw new IllegalFormatFlagsException("FilterMethod=" + ihdr.getFilterMethod());
                    if (ihdr.getInterlaceMethod() != 0)
                        throw new IllegalFormatFlagsException("InterlaceMethod=" + ihdr.getInterlaceMethod());
                    if (ihdr.getCompressMethod() != 0)
                        throw new IllegalFormatFlagsException("CompressMethod=" + ihdr.getCompressMethod());
                    ihdrIndex = chunkIndex;
                    continue;
                case CODE_PLTE:
                    plteIndex = chunkIndex;
                    continue;
                case CODE_tRNS:
                    trnsIndex = chunkIndex;
                    continue;
                case CODE_IDAT:
                case CODE_fdAT:
                    if (plteIndex >= 0) break;
                default:
                    continue;
            }

            // get new appeared colors in this frame
            byte[] data = ang.getChunks().get(plteIndex).getData();
            byte[] alpha = ang.getChunks().get(trnsIndex).getData();
            int count = colorsCount(data);
            Color[] colorTable = new Color[count];
            for (int i = 0; i < count; i++) {
                allCount++;
                colorTable[0] = readColor(data, alpha, i);
            }


            // decompress image pixels
            byte[] img = unzipImageDAT(chunk.getData());
            compressTest(chunk.getData().length - 12, img);
            frameImages.add(new FrameImage(
                    decodeImagePixels(img, ihdr.getBitDepth(), colorTable),
                    ihdrIndex, plteIndex, trnsIndex, chunkIndex
            ));

//            log.debug(String.format("%d %d %d %d %d",
//                    ihdr.getBitDepth(),
//                    ihdr.getColorType(),
//                    ihdr.getCompressMethod(),
//                    ihdr.getFilterMethod(),
//                    ihdr.getInterlaceMethod()));

            plteIndex = -1;
            trnsIndex = -1;
        }

        int pixelsCount = 0;
        for (FrameImage frame : frameImages) pixelsCount += frame.pixels.length;
        Color[] pixels = new Color[pixelsCount];
        int pixelsIdx = 0;
        for (FrameImage frame : frameImages) {
            System.arraycopy(frame.pixels, 0, pixels, pixelsIdx, frame.pixels.length);
            pixelsIdx += frame.pixels.length;
        }


        log.debug(String.format("pixels: %d , colors: %d",
                pixels.length,
                allCount));
    }

    /**
     * test compress methods
     */
    private void compressTest(int origSize, byte[] imgData) {
        Deflater deflater = new Deflater(0, false);
        deflater.setInput(imgData);
        deflater.finish();
        byte[] out = new byte[imgData.length];
        int zlib = deflater.deflate(out);
        log.debug(String.format("len: %d, tiny: %d, zlib: %d",
                imgData.length,
                origSize,
                zlib));
    }

    /**
     * decompress IDAT/fDAT
     */
    private byte[] unzipImageDAT(byte[] chunkDAT) throws DataFormatException {
        // Decompress the bytes
        Inflater inflater = new Inflater();
        inflater.setInput(chunkDAT, 8, chunkDAT.length - 12);
        byte[] data = new byte[chunkDAT.length];
        int len = inflater.inflate(data);
        inflater.end();
        byte[] result = new byte[len];
        System.arraycopy(data, 0, result, 0, len);
        return result;
    }

    /**
     * decode image pixels
     */
    private Color[] decodeImagePixels(byte[] imageData, final int bitDepth, Color[] colorTable) {
        Color[] pixels = new Color[imageData.length * 8 / bitDepth];
        int i = 0;
        for (byte b : imageData) {
            switch (bitDepth) {
                case 1:
                    for (int x = 0; x < 8; x++) pixels[i++] = colorTable[b >> 1 & 0x1];
                    continue;
                case 2:
                    for (int x = 0; x < 4; x++) pixels[i++] = colorTable[b >> 2 & 0x3];
                    continue;
                case 4:
                    for (int x = 0; x < 2; x++) pixels[i++] = colorTable[b >> 4 & 0xF];
                    continue;
                case 8:
                    pixels[i++] = colorTable[b & 0xFF];
                    continue;
                default:
                    throw new IllegalFormatFlagsException("bitDepth=" + bitDepth);
            }
        }
        return pixels;
    }

    /**
     * optimize colors order in palettes for better patch reduce
     */
    private ArrayList<Palette> optmizePalette(ArrayList<Palette> palettes) {

        ArrayList<Integer> sameCount = new ArrayList<>();
        for (int i = 1; i < palettes.size(); i++) {
            HashSet<Color> preColors = new HashSet<>();
            preColors.addAll(palettes.get(i - 1).colors);
            ArrayList<Color> curColors = palettes.get(i).colors;
            int count = 0;
            for (Color color : curColors) {
                if (preColors.contains(color)) count++;
            }
            sameCount.add(count);
        }

        for (int count : sameCount) {
            log.debug(String.format("sameCount: %d",
                    count));
        }
        return null;
    }

    /**
     * generate palettes with Merged Color to the most previous one
     */
    private ArrayList<Palette> genPalettes(AngData ang) {
        Palette pre = null;
        ArrayList<Palette> palettes = new ArrayList<>();
        ArrayList<Color> newColors = new ArrayList<>();
        int chunkIndex = -1;
        int plteIndex = -1;
        int trnsIndex = -1;
        for (AngChunkData chunk : ang.getChunks()) {
            chunkIndex++;
            switch (chunk.getTypeCode()) {
                case CODE_PLTE:
                    plteIndex = chunkIndex;
                    continue;
                case CODE_tRNS:
                    trnsIndex = chunkIndex;
                    continue;
                case CODE_IDAT:
                case CODE_fdAT:
                    if (plteIndex >= 0) break;
                default:
                    continue;
            }

            if (pre == null) {
                pre = new Palette(plteIndex, trnsIndex);
            }

            // get new appeared colors in this frame
            byte[] data = ang.getChunks().get(plteIndex).getData();
            byte[] alpha = ang.getChunks().get(trnsIndex).getData();
            int count = colorsCount(data);
            newColors.clear();
            HashSet<Color> preColors = new HashSet<>();
            preColors.addAll(pre.colors);
            for (int i = 0; i < count; i++) {
                Color newColor = readColor(data, alpha, i);
                if (!preColors.contains(newColor)) {
                    newColors.add(newColor);
                }
            }

            if (pre.colors.size() + newColors.size() > 256) {
                palettes.add(pre);
                pre = new Palette(plteIndex, trnsIndex);
                pre.colors.addAll(newColors);
            } else {
                pre.colors.addAll(newColors);
            }

            plteIndex = -1;
            trnsIndex = -1;
        }
        if (pre != null) palettes.add(pre);

        for (Palette p : palettes) {
            log.debug(String.format("chunk: %d, frame: %d, colors: %d",
                    p.plteIndex,
                    ang.getChunks().get(p.plteIndex).getFrameIndex(),
                    p.colors.size()));
        }

        return palettes;
    }

    private int colorsCount(byte[] plteChunkData) {
        return (plteChunkData.length - 12) / 3;
    }

    /**
     * read color from PLTE & tRNS chunks data
     *
     * @param plteChunkData not null
     * @param trnsChunkData can be null, then alpha is 255
     * @param index         color index
     * @return color
     */
    private Color readColor(byte[] plteChunkData, byte[] trnsChunkData, int index) {
        int alpha = 255;
        if (trnsChunkData != null && index < trnsChunkData.length)
            alpha = trnsChunkData[index];
        int off = 8 + index * 3;
        return new Color(plteChunkData[off] & 0xFF,
                plteChunkData[off + 1] & 0xFF,
                plteChunkData[off + 2] & 0xFF,
                alpha & 0xFF);
    }

    /**
     * write color to PLTE & rRNS chunk data
     *
     * @param chunkData     not null
     * @param trnsChunkData can be null
     * @param index         color index
     * @param color         color
     */
    private void writeColor(byte[] chunkData, byte[] trnsChunkData, int index, Color color) {
        int off = 8 + index * 3;
        chunkData[off++] = (byte) (color.getRed() & 0xFF);
        chunkData[off++] = (byte) (color.getGreen() & 0xFF);
        chunkData[off] = (byte) (color.getBlue() & 0xFF);
        if (trnsChunkData != null && index < trnsChunkData.length) {
            trnsChunkData[index] = (byte) (color.getAlpha() & 0xFF);
        }
    }

    private static class Palette {
        ArrayList<Color> colors = new ArrayList<>();
        int plteIndex;
        int trnsIndex;

        public Palette(int plteIndex, int trnsIndex) {
            this.plteIndex = plteIndex;
            this.trnsIndex = trnsIndex;
        }
    }

    private static class FrameImage {
        Color[] pixels;
        int ihdrIndex;
        int plteIndex;
        int trnsIndex;
        int datIndex;

        public FrameImage(Color[] pixels, int ihdrIndex, int plteIndex, int trnsIndex, int datIndex) {
            this.pixels = pixels;
            this.ihdrIndex = ihdrIndex;
            this.plteIndex = plteIndex;
            this.trnsIndex = trnsIndex;
            this.datIndex = datIndex;
        }
    }
}
