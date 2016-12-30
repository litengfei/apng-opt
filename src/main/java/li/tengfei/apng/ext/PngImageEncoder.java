package li.tengfei.apng.ext;

import li.tengfei.apng.base.ApngIHDRChunk;
import li.tengfei.apng.opt.builder.PngChunkData;
import lu.luz.jzopfli.ZopfliH;
import lu.luz.jzopfli.Zopfli_lib;

import java.awt.*;
import java.util.*;
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
     * make IHDR with defined bitDepth, use colorType=3, compressMethod=0,
     *
     * @param oldIHDR
     * @param bitDepth
     * @return
     */
    private static PngChunkData makeIHDR(ApngIHDRChunk oldIHDR,
                                         int bitDepth,
                                         int interlaceMethod) {
        byte data[] = new byte[25];
        intToArray(13, data, 0);
        intToArray(CODE_IHDR, data, 4);
        intToArray(oldIHDR.getWidth(), data, 8);
        intToArray(oldIHDR.getHeight(), data, 12);
        data[16] = (byte) (bitDepth & 0xff);        // bitDepth;
        data[17] = 3;                               // colorType;
        data[18] = 0;                               // compressMethod;
        data[19] = 0;                               // filterMethod;
        data[20] = (byte) (interlaceMethod & 0xff); // interlaceMethod;
        CRC32 crc32 = new CRC32();
        crc32.update(data, 4, 17);
        intToArray((int) crc32.getValue(), data, 21);
        return new PngChunkData(data, CODE_IHDR);
    }

    /**
     * encode image into indexed ColorType chunks: IDHR + PLTE [ + TRNS ] IDAT
     */
    public ArrayList<PngChunkData> encode(Color[] pixels, Map<Color, Color> map, byte[] ihdrData) {
        HashSet<Color> set = new HashSet<>(256);
        set.addAll(map.values());
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

        // compute color index in color table
        ApngIHDRChunk ihdr = new ApngIHDRChunk();
        ihdr.parse(new ByteArrayPngChunk(ihdrData));

        // compute color index in color table
        HashMap<Color, Integer> colorIndex = new HashMap<>(colorTable.length);
        for (int i = 0; i < colorTable.length; i++) {
            colorIndex.put(colorTable[i], i);
        }

        // calculate bitDepth
        int maxValue = colorTable.length - 1;
        int bitDepth;
        if (maxValue < 2) bitDepth = 1;
        else if (maxValue < 4) bitDepth = 2;
        else if (maxValue < 16) bitDepth = 4;
        else bitDepth = 8;

        // generate indexed image data
        byte[] data = new byte[pixels.length * bitDepth / 8 + ihdr.getHeight()];
        final int rowBytes = data.length / ihdr.getHeight();

        int p = 0;
        for (int i = 0; i < data.length; i++) {
            if (i % rowBytes == 0) {
                data[i] = 0;
                continue;
            }
            byte dataByte = 0;
            switch (bitDepth) {
                case 1:
                    for (int x = 0; x < 8; x++) {
                        dataByte = (byte) ((dataByte << 1) | (colorIndex.get(map.get(pixels[p++])) & 0x1));
                    }
                    break;
                case 2:
                    for (int x = 0; x < 4; x++) {
                        dataByte = (byte) ((dataByte << 2) | (colorIndex.get(map.get(pixels[p++])) & 0x3));
                    }
                    break;
                case 4:
                    for (int x = 0; x < 2; x++) {
                        dataByte = (byte) ((dataByte << 4) | (colorIndex.get(map.get(pixels[p++])) & 0xf));
                    }
                    break;
                case 8:
                    dataByte = (byte) (colorIndex.get(map.get(pixels[p++])) & 0xff);
            }

            data[i] = dataByte;
        }
        return encode(ihdr, data, colorTable, bitDepth);
    }


    /**
     * encode image into indexed ColorType chunks: IDHR + PLTE [ + TRNS ] IDAT
     */
    private ArrayList<PngChunkData> encode(ApngIHDRChunk oldIHDR,
                                           byte[] imgData,
                                           Color[] colorTable,
                                           int bitDepth) {
        ArrayList<PngChunkData> chunks = new ArrayList<>();
        // make IHDR


        chunks.add(makeIHDR(oldIHDR, bitDepth, oldIHDR.getInterlaceMethod()));

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
