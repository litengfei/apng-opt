package li.tengfei.apng.ext;

import li.tengfei.apng.opt.builder.PngChunkData;
import lu.luz.jzopfli.ZopfliH;
import lu.luz.jzopfli.Zopfli_lib;

import java.awt.*;
import java.util.ArrayList;
import java.util.IllegalFormatFlagsException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import static li.tengfei.apng.base.ApngConst.*;
import static li.tengfei.apng.base.PngStream.intToArray;

/**
 * Png Image Indexed Encoder
 *
 * @author ltf
 * @since 16/12/26, 下午4:14
 */
public class PngImageEncoder {
    private CRC32 crc32 = new CRC32();

    /**
     * make IHDR with defined bitDepth, use colorType=3, compressMethod=0, interlaceMethod=0
     */
    private static PngChunkData makeIHDR(int width, int height,
                                         int bitDepth) {
        byte data[] = new byte[25];
        intToArray(13, data, 0);
        intToArray(CODE_IHDR, data, 4);
        intToArray(width, data, 8);
        intToArray(height, data, 12);
        data[16] = (byte) (bitDepth & 0xff);        // bitDepth;
        data[17] = 3;                               // colorType;
        data[18] = 0;                               // compressMethod;
        data[19] = 0;                               // filterMethod;
        data[20] = 0;                               // interlaceMethod;
        CRC32 crc32 = new CRC32();
        crc32.update(data, 4, 17);
        intToArray((int) crc32.getValue(), data, 21);
        return new PngChunkData(data, CODE_IHDR);
    }

    /**
     * encode image into indexed ColorType chunks: IDHR + PLTE [ + TRNS ] IDAT
     */
    public ArrayList<PngChunkData> encode(byte[] pixelIndexes, final int height, Color[] colorTable) {
        if (pixelIndexes.length % height != 0)
            throw new IllegalFormatFlagsException("pixels % height != 0, not a legal image");
        if (colorTable.length > 256)
            throw new IllegalFormatFlagsException("only support max 256 colors table");
        final int width = pixelIndexes.length / height;

        // calculate bitDepth
        int maxValue = colorTable.length - 1;
        int bitDepth;
        if (maxValue < 2) bitDepth = 1;
        else if (maxValue < 4) bitDepth = 2;
        else if (maxValue < 16) bitDepth = 4;
        else bitDepth = 8;

        // generate indexed image data
        int rowBytes = width * bitDepth / 8;
        final int lastBlank = 8 - (width * bitDepth % 8);
        if (lastBlank != 8) rowBytes++;
        rowBytes++;// add filter type byte
        byte[] data = new byte[rowBytes * height];

        int p = 0;
        for (int i = 0; i < data.length; i++) {
            if (i % rowBytes == 0) {
                data[i] = 0;
                continue;
            }
            byte dataByte = 0;
            int skip = lastBlank == 0 ? 0 : i % rowBytes == rowBytes - 1 ? lastBlank : 0;
            switch (bitDepth) {
                case 1:
                    for (int x = 0; x < 8 - skip; x++) {
                        dataByte = (byte) ((dataByte << 1) | (pixelIndexes[p++] & 0x1) & 0xff);
                    }
                    if (skip > 0) dataByte = (byte) ((dataByte << skip) & 0xff);
                    break;
                case 2:
                    for (int x = 0; x < 4 - skip; x++) {
                        dataByte = (byte) ((dataByte << 2) | (pixelIndexes[p++] & 0x3) & 0xff);
                    }
                    if (skip > 0) dataByte = (byte) ((dataByte << skip * 2) & 0xff);
                    break;
                case 4:
                    for (int x = 0; x < 2 - skip; x++) {
                        dataByte = (byte) ((dataByte << 4) | (pixelIndexes[p++] & 0xf) & 0xff);
                    }
                    if (skip > 0) dataByte = (byte) ((dataByte << skip * 4) & 0xff);
                    break;
                case 8:
                    dataByte = (byte) (pixelIndexes[p++] & 0xff);
            }

            data[i] = dataByte;
        }
//        data = filterOpt(data, rowBytes, height);
//        data = filterOpt(data, rowBytes, height);
//        data = filterOpt(data, rowBytes, height);
        return encode(width, height, bitDepth, data, colorTable);
    }

    private byte[] filterOpt(byte[] noFilterImgData, int rowBytes, int height) {
        byte[] out = new byte[noFilterImgData.length];
        byte[] zbuf = new byte[noFilterImgData.length];
        System.arraycopy(noFilterImgData, 0, out, 0, noFilterImgData.length);
        for (int y = 0; y < height; y++) {
            int pos = y * rowBytes;
            int min = Integer.MAX_VALUE;
            byte bestFilter = 0;
            for (byte filter = 0; filter < PngFilters.filters.length; filter++) {
                out[pos] = (byte) (filter & 0xff);
                PngFilters.filt(noFilterImgData, out, pos, rowBytes, 1);
                int sz = zlibCompress(out, zbuf);
                if (sz < min) {
                    min = sz;
                    bestFilter = filter;
                }
            }

            if (out[pos] != bestFilter) {
                out[pos] = (byte) (bestFilter & 0xff);
                PngFilters.filt(noFilterImgData, out, pos, rowBytes, 1);
            }
        }

        return out;
    }

    /**
     * encode image into indexed ColorType chunks: IDHR + PLTE [ + TRNS ] IDAT
     */
    private ArrayList<PngChunkData> encode(int width, int height, int bitDepth,
                                           byte[] imgData,
                                           Color[] colorTable) {
        ArrayList<PngChunkData> chunks = new ArrayList<>();
        // make IHDR


        chunks.add(makeIHDR(width, height, bitDepth));

        // make plte & trns
        int plteSize = colorTable.length * 3 + 12;
        int trnsSize = colorTable.length + 12;
        // trns may short than plte
        for (int i = colorTable.length - 1; i >= 0; i--) {
            if (colorTable[i].getAlpha() != 255) break;
            trnsSize--;
        }
        byte[] plte = new byte[plteSize];
        byte[] trns = new byte[trnsSize];
        intToArray(plteSize - 12, plte, 0);
        intToArray(CODE_PLTE, plte, 4);
        intToArray(trnsSize - 12, trns, 0);
        intToArray(CODE_tRNS, trns, 4);

        // write colors
        for (int i = 0; i < colorTable.length; i++) writeColor(plte, trns, i, colorTable[i]);


        CRC32 crc32 = new CRC32();
        crc32.update(plte, 4, plteSize - 8);
        intToArray((int) crc32.getValue(), plte, plteSize - 4);
        crc32.reset();
        crc32.update(trns, 4, trnsSize - 8);
        intToArray((int) crc32.getValue(), trns, trnsSize - 4);
        // add plte
        chunks.add(new PngChunkData(plte, CODE_PLTE));

        // check alpha then add trns if needed
        boolean withAlpha = false;
        for (Color c : colorTable) {
            if (c.getAlpha() != 255) {
                withAlpha = true;
                break;
            }
        }
        if (withAlpha) chunks.add(new PngChunkData(trns, CODE_tRNS));

        // add dat chunk at last
        chunks.add(makeDATchunk(imgData));
        return chunks;
    }


    /**
     * read color from PLTE & tRNS chunks data
     */
    private void writeColor(byte[] plteChunkData, byte[] trnsChunkData, int index, Color color) {
        int off = 8 + index * 3;
        plteChunkData[off++] = (byte) (color.getRed() & 0xFF);
        plteChunkData[off++] = (byte) (color.getGreen() & 0xFF);
        plteChunkData[off] = (byte) (color.getBlue() & 0xFF);
        // trns may short than plte
        if (8 + index < trnsChunkData.length - 4)
            trnsChunkData[8 + index] = (byte) (color.getAlpha() & 0xFF);
    }

    /**
     * make DAT chunk with image data
     */
    private PngChunkData makeDATchunk(byte[] imgData) {
        byte[] buf = new byte[imgData.length * 2];
        //int len = zlibCompress(imgData, buf);
        int len = zopfliCompress(imgData, buf);
        if (len >= buf.length) throw new IllegalStateException("It's more big after optimized, stop!");

        byte[] chunkDat = new byte[len + 12];
        intToArray(len, chunkDat, 0);
        intToArray(CODE_IDAT, chunkDat, 4);
        System.arraycopy(buf, 0, chunkDat, 8, len);
        crc32.reset();
        crc32.update(chunkDat, 4, len + 4);
        intToArray((int) crc32.getValue(), chunkDat, len + 8);
        return new PngChunkData(chunkDat, CODE_IDAT);
    }

    /**
     * zlib compress image data
     */
    private int zlibCompress(byte[] imgData, byte[] outBuf) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(imgData);
        deflater.finish();
        int zlib = deflater.deflate(outBuf);
        deflater.end();
        return zlib;
    }

    /**
     * zopfli compress image data
     */
    private int zopfliCompress(byte[] imgData, byte[] outBuf) {
        ZopfliH.ZopfliOptions options = new ZopfliH.ZopfliOptions();
        options.numiterations = 15;
        byte[][] out = {{0}};
        int[] outsize = {0};

        Zopfli_lib.ZopfliCompress(options,
                ZopfliH.ZopfliFormat.ZOPFLI_FORMAT_ZLIB,
                imgData, imgData.length,
                out, outsize);

        int size = outsize[0] > outBuf.length ? outBuf.length : outsize[0];

        System.arraycopy(out[0], 0, outBuf, 0, size);
        return size;
    }
}
