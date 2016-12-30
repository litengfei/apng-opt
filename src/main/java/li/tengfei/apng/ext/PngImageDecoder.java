package li.tengfei.apng.ext;

import li.tengfei.apng.base.ApngIHDRChunk;
import li.tengfei.apng.opt.builder.PngChunkData;

import java.awt.*;
import java.util.ArrayList;
import java.util.IllegalFormatFlagsException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static li.tengfei.apng.base.ApngConst.*;

/**
 * Decode Png Image
 *
 * @author ltf
 * @since 16/12/26, 下午4:01
 */
public class PngImageDecoder {

    /**
     * decode frameImages from chunks
     */
    public ArrayList<PngImage> decodeImages(ArrayList<PngChunkData> chunks) throws DataFormatException {
        ArrayList<PngImage> frameImages = new ArrayList<>();

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

        return frameImages;
    }

    private PngImage processImageData(ArrayList<PngChunkData> chunks,
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
        if (ihdr.getBitDepth() > 8)
            throw new IllegalFormatFlagsException("Unsupported BitDepth=" + ihdr.getCompressMethod());

        ArrayList<byte[]> datas = new ArrayList<>();
        int size = 0;
        for (int idx = dataBeginIndex; idx <= dataEndIndex; idx++) {
            // decompress image pixels
            byte[] dat = chunks.get(idx).getData();
            size += dat.length - 12;
            datas.add(dat);
        }

        byte[] data = new byte[size];
        int off = 0;
        for (byte[] dat : datas) {
            System.arraycopy(dat, 8, data, off, dat.length - 12);
            off += dat.length - 12;
        }
        datas.clear();
        data = unzipImageDAT(data);

        Color[] pixels;
        switch (ihdr.getColorType()) {
            case 0:
                pixels = decodeColorPixels(data, ihdr.getHeight(),
                        ihdr.getBitDepth(), false, false);
                break;
            case 2:
                pixels = decodeColorPixels(data, ihdr.getHeight(),
                        ihdr.getBitDepth(), true, false);
                break;
            case 3:
                // get new appeared colors in this frame
                byte[] plte = chunks.get(plteIndex).getData();
                byte[] trns = chunks.get(trnsIndex).getData();
                int count = colorsCount(plte);
                Color[] colorTable = new Color[count];
                for (int i = 0; i < count; i++) {
                    colorTable[i] = readColor(plte, trns, i);
                }
                pixels = decodeIndexedPixels(data, ihdr.getHeight(),
                        ihdr.getBitDepth(), colorTable);
                break;
            case 4:
                pixels = decodeColorPixels(data, ihdr.getHeight(),
                        ihdr.getBitDepth(), false, true);
                break;
            case 6:
                pixels = decodeColorPixels(data, ihdr.getHeight(),
                        ihdr.getBitDepth(), true, true);
                break;
            default:
                throw new IllegalFormatFlagsException("colorType=" + ihdr.getColorType());
        }

        return new PngImage(pixels, ihdrIndex, plteIndex, trnsIndex, dataBeginIndex, dataEndIndex);
    }

    /**
     * decompress IDAT/fDAT
     */
    private byte[] unzipImageDAT(byte[] compressedData) throws DataFormatException {
        // Decompress the bytes
        Inflater inflater = new Inflater();
        inflater.setInput(compressedData, 0, compressedData.length);
        ArrayList<byte[]> datas = new ArrayList<>();
        int all = 0;
        byte[] data = new byte[compressedData.length];
        int len = inflater.inflate(data);
        all += len;
        datas.add(data);
        while (len == data.length) {
            data = new byte[data.length];
            len = inflater.inflate(data);
            all += len;
            datas.add(data);
        }
        inflater.end();
        byte[] result = new byte[all];
        for (int i = 0; i < datas.size(); i++) {
            int size = data.length;
            if (i == datas.size() - 1) size = len;
            System.arraycopy(datas.get(i), 0, result, i * data.length, size);
        }
        return result;
    }

    /**
     * reconstruct image data
     *
     * @param filteredData filtered data
     * @param height       image height
     * @param pixelBytes   bytes count in one pixel (>=1)
     * @return
     */
    private byte[] reconstructImageDAT(byte[] filteredData, final int height, int pixelBytes) {
        final int rowBytes = filteredData.length / height;
        byte[] reconstructedData = new byte[filteredData.length];
        for (int i = 0; i < height; i++) {
            PngFilters.recon(filteredData, reconstructedData,
                    i * rowBytes, rowBytes, pixelBytes);
        }

        return reconstructedData;
    }

    /**
     * decode color image pixels [ colorType = 0,1, 4,5 ]
     */
    private Color[] decodeColorPixels(byte[] imageData,
                                      final int height,
                                      final int bitDepth,
                                      boolean isRGB,
                                      boolean withAlpha) {
        int sampleCount = 1;
        if (isRGB) sampleCount = 3;
        if (withAlpha) sampleCount++;
        Color[] pixels = new Color[(imageData.length - height) * 8 / (bitDepth * sampleCount)];
        final int rowBytes = imageData.length / height;

        imageData = reconstructImageDAT(imageData, height, sampleCount);
        if (!isRGB && !withAlpha & bitDepth < 8) {
            // Greyscale with 1, 2, 4, bitDepth
            int i = 0, dataIndex = 0;
            for (byte b : imageData) {
                if (dataIndex++ % rowBytes == 0) continue;
                switch (bitDepth) {
                    case 1:
                        for (int x = 0; x < 8; x++) {
                            int v = ((b >> (7 - x)) & 0x1) * 255;
                            pixels[i++] = new Color(v, v, v);
                        }
                        continue;
                    case 2:
                        for (int x = 0; x < 4; x++) {
                            int v = ((b >> ((3 - x) * 2)) & 0x3) * 85;
                            pixels[i++] = new Color(v, v, v);
                        }
                        continue;
                    case 4:
                        for (int x = 0; x < 2; x++) {
                            int v = (((b >> ((1 - x) * 4)) & 0xF) * 17);
                            pixels[i++] = new Color(v, v, v);
                        }
                        continue;
                    default:
                        throw new IllegalFormatFlagsException("bitDepth=" + bitDepth);
                }
            }
        } else {
            // Greyscale or RGB with 8 bitDepth  ( bitDepth 16 not supported now )
            int step = sampleCount * bitDepth / 8;
            int i = 0;
            for (int p = 0; p < imageData.length; ) {
                if (p % rowBytes == 0) {
                    if (imageData[p] != 0) System.out.println(imageData[p]);
                    p++;
                    continue;
                }
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
                p += step;
            }
        }
        return pixels;
    }

    /**
     * decode indexed image pixels [ colorType = 3,5 ]
     */
    private Color[] decodeIndexedPixels(byte[] imageData,
                                        final int height,
                                        final int bitDepth,
                                        Color[] colorTable) {
        Color[] pixels = new Color[(imageData.length - height) * 8 / bitDepth];
        int i = 0, dataIndex = 0;
        final int rowBytes = imageData.length / height;
        imageData = reconstructImageDAT(imageData, height, 1);
        for (byte b : imageData) {
            if (dataIndex++ % rowBytes == 0) continue;
            switch (bitDepth) {
                case 1:
                    for (int x = 0; x < 8; x++) pixels[i++] = colorTable[(b >> (7 - x)) & 0x1];
                    continue;
                case 2:
                    for (int x = 0; x < 4; x++) pixels[i++] = colorTable[(b >> ((3 - x) * 2)) & 0x3];
                    continue;
                case 4:
                    for (int x = 0; x < 2; x++) pixels[i++] = colorTable[(b >> ((1 - x) * 4)) & 0xF];
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
        // trns may be short than plte
        if (trnsChunkData != null && index < trnsChunkData.length - 12)
            alpha = trnsChunkData[8 + index];
        int off = 8 + index * 3;
        return new Color(plteChunkData[off] & 0xFF,
                plteChunkData[off + 1] & 0xFF,
                plteChunkData[off + 2] & 0xFF,
                alpha & 0xFF);
    }
}
