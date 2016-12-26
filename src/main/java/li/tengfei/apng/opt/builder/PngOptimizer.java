package li.tengfei.apng.opt.builder;

import li.tengfei.apng.base.ApngIHDRChunk;
import li.tengfei.apng.base.FormatNotSupportException;
import li.tengfei.apng.ext.ByteArrayPngChunk;
import li.tengfei.apng.opt.optimizer.ChunkTypeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IllegalFormatFlagsException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import static li.tengfei.apng.base.ApngConst.*;

/**
 * png size optimizer
 *
 * @author ltf
 * @since 16/12/26, 上午10:13
 */
public class PngOptimizer {
    private static final Logger log = LoggerFactory.getLogger(PngOptimizer.class);

    private PngData mPng;

    private ArrayList<PngData> mFrameDatas;

    private ArrayList<PngChunkData> mFctlChunks = new ArrayList<>();

    private PngChunkData mActlChunk;

    public boolean optimize(String srcApngFile, String outFile) {
        try {
            mPng = new PngReader(srcApngFile).getPngData();
            //
            colorReduce(mPng.chunks);

            for (PngChunkData chunk : mPng.chunks) {
                log.debug(ChunkTypeHelper.getTypeName(chunk.typeCode));
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (FormatNotSupportException e) {
            e.printStackTrace();
        } catch (DataFormatException e) {
            e.printStackTrace();
        }
        return false;
    }


    /**
     * reduce colors
     */
    private void colorReduce(ArrayList<PngChunkData> chunks) throws DataFormatException {
        ArrayList<FrameImage> frameImages = new ArrayList<>();

        int ihdrIndex = -1;
        int plteIndex = -1;
        int trnsIndex = -1;
        int dataBegin = -1;
        for (int i = 0; i < chunks.size(); i++) {
            PngChunkData chunk = chunks.get(i);
            if (dataBegin > 0 && chunk.getTypeCode() != CODE_IDAT && chunk.getTypeCode() != CODE_fdAT) {
                frameImages.add(processImageData(chunks, ihdrIndex, plteIndex, trnsIndex, dataBegin, i - 1));
                dataBegin = -1;
                plteIndex = -1;
                trnsIndex = -1;
            }

            switch (chunk.getTypeCode()) {
                case CODE_IHDR:
                    ihdrIndex = i;
                    continue;
                case CODE_PLTE:
                    plteIndex = i;
                    continue;
                case CODE_tRNS:
                    trnsIndex = i;
                    continue;
                case CODE_IDAT:
                case CODE_fdAT:
                    if (dataBegin < 0) {
                        dataBegin = i;
                    }
            }
        }
        if (dataBegin > 0) {
            frameImages.add(processImageData(chunks, ihdrIndex, plteIndex, trnsIndex, dataBegin, chunks.size() - 1));
        }

//        int pixelsCount = 0;
//        for (FrameImage frame : frameImages) pixelsCount += frame.pixels.length;
//        Color[] pixels = new Color[pixelsCount];
//        int pixelsIdx = 0;
//        for (FrameImage frame : frameImages) {
//            System.arraycopy(frame.pixels, 0, pixels, pixelsIdx, frame.pixels.length);
//            pixelsIdx += frame.pixels.length;
//        }
//
//
//        log.debug(String.format("pixels: %d , colors: %d",
//                pixels.length,
//                allCount));
    }

    private FrameImage processImageData(ArrayList<PngChunkData> chunks,
                                        int ihdrIndex,
                                        int plteIndex,
                                        int trnsIndex,
                                        int dataBeginIndex,
                                        int dataEndIndex) throws DataFormatException {
        ApngIHDRChunk ihdr = new ApngIHDRChunk();
        ihdr.parse(new ByteArrayPngChunk(chunks.get(ihdrIndex).getData()));
        if (ihdr.getFilterMethod() != 0)
            throw new IllegalFormatFlagsException("FilterMethod=" + ihdr.getFilterMethod());
        if (ihdr.getInterlaceMethod() != 0)
            throw new IllegalFormatFlagsException("InterlaceMethod=" + ihdr.getInterlaceMethod());
        if (ihdr.getCompressMethod() != 0)
            throw new IllegalFormatFlagsException("CompressMethod=" + ihdr.getCompressMethod());

        ArrayList<byte[]> datas = new ArrayList<>();
        int size = 0;
        for (int idx = dataBeginIndex; idx <= dataEndIndex; idx++) {
            // decompress image pixels
            byte[] dat = chunks.get(idx).getData();
            size += dat.length;
            datas.add(dat);
        }

        byte[] data = new byte[size];
        int off = 0;
        for (byte[] dat : datas) {
            System.arraycopy(dat, 0, data, off, dat.length);
            off += dat.length;
        }
        datas.clear();
        data = unzipImageDAT(data);

        Color[] pixels;
        switch (ihdr.getColorType()) {
            case 0:
                pixels = decodeColorPixels(data, ihdr.getBitDepth(), false, false);
                break;
            case 2:
                pixels = decodeColorPixels(data, ihdr.getBitDepth(), true, false);
                break;
            case 3:
                // get new appeared colors in this frame
                byte[] plte = chunks.get(plteIndex).getData();
                byte[] trns = chunks.get(trnsIndex).getData();
                int count = colorsCount(plte);
                Color[] colorTable = new Color[count];
                for (int i = 0; i < count; i++) {
                    colorTable[0] = readColor(plte, trns, i);
                }
                pixels = decodeIndexedPixels(data, ihdr.getBitDepth(), colorTable);
                break;
            case 4:
                pixels = decodeColorPixels(data, ihdr.getBitDepth(), false, true);
                break;
            case 6:
                pixels = decodeColorPixels(data, ihdr.getBitDepth(), true, true);
                break;
            default:
                throw new IllegalFormatFlagsException("colorType=" + ihdr.getColorType());
        }

        return new FrameImage(pixels, ihdrIndex, plteIndex, trnsIndex, dataBeginIndex, dataEndIndex);
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
     * decode color image pixels [ colorType = 0,1, 4,5 ]
     */
    private Color[] decodeColorPixels(byte[] imageData, final int bitDepth,
                                      boolean isRGB,
                                      boolean withAlpha) {
        int sampleCount = 1;
        if (isRGB) sampleCount = 3;
        if (withAlpha) sampleCount++;
        Color[] pixels = new Color[imageData.length * 8 / bitDepth * sampleCount];

        if (!isRGB && !withAlpha & bitDepth < 8) {
            // Greyscale with 1, 2, 4, bitDepth
            int i = 0;
            for (byte b : imageData) {
                switch (bitDepth) {
                    case 1:
                        for (int x = 0; x < 8; x++) {
                            int v = b >> 1 & 0x1;
                            pixels[i++] = new Color(v, v, v);
                        }
                        continue;
                    case 2:
                        for (int x = 0; x < 4; x++) {
                            int v = b >> 2 & 0x3;
                            pixels[i++] = new Color(v, v, v);
                        }
                        continue;
                    case 4:
                        for (int x = 0; x < 2; x++) {
                            int v = b >> 4 & 0xF;
                            pixels[i++] = new Color(v, v, v);
                        }
                        continue;
                    default:
                        throw new IllegalFormatFlagsException("bitDepth=" + bitDepth);
                }
            }
        } else {
            // Greyscale or RGB with 8,16 bitDepth
            int step = sampleCount * bitDepth / 8;
            int i = 0;
            for (int p = 0; p < imageData.length; p += step) {
                int R, G, B, A;
                int off = p;
                R = imageData[off++];
                if (isRGB) {
                    G = imageData[off++];
                    B = imageData[off++];
                } else {
                    G = R;
                    B = R;
                }
                if (withAlpha) A = imageData[off];
                else A = 255;
                pixels[i++] = new Color(R & 0xFF, G & 0xFF, B & 0xFF, A & 0xFF);
            }
        }
        return pixels;
    }

    /**
     * decode indexed image pixels [ colorType = 3,5 ]
     */
    private Color[] decodeIndexedPixels(byte[] imageData, final int bitDepth, Color[] colorTable) {
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

    private static class FrameImage {
        Color[] pixels;
        int ihdrIndex;
        int plteIndex;
        int trnsIndex;
        int datBeginIndex;
        int datEndIndex;

        public FrameImage(Color[] pixels,
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
    }
}
