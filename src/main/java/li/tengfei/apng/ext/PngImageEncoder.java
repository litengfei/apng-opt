package li.tengfei.apng.ext;

import li.tengfei.apng.base.ApngIHDRChunk;
import li.tengfei.apng.opt.builder.PngChunkData;
import org.meteogroup.jbrotli.Brotli;
import org.meteogroup.jbrotli.BrotliCompressor;
import ru.eustas.zopfli.Buffer;
import ru.eustas.zopfli.Options;
import ru.eustas.zopfli.Zopfli;

import java.awt.*;
import java.util.ArrayList;
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
                                         int filterMethod,
                                         int interlaceMethod) {
        byte data[] = new byte[25];
        intToArray(13, data, 0);
        intToArray(CODE_IHDR, data, 4);
        intToArray(oldIHDR.getWidth(), data, 8);
        intToArray(oldIHDR.getHeight(), data, 12);
        data[16] = (byte) (bitDepth & 0xff);        // bitDepth;
        data[17] = 3;                               // colorType;
        data[18] = 0;                               // compressMethod;
        data[19] = (byte) (filterMethod & 0xff);    // filterMethod;
        data[20] = (byte) (interlaceMethod & 0xff); // interlaceMethod;
        CRC32 crc32 = new CRC32();
        crc32.update(data, 4, 17);
        intToArray((int) crc32.getValue(), data, 21);
        return new PngChunkData(data, CODE_IHDR);
    }

    /**
     * encode image into indexed ColorType chunks: IDHR + PLTE [ + TRNS ] IDAT
     */
    public ArrayList<PngChunkData> encode(ApngIHDRChunk oldIHDR,
                                          byte[] imgData,
                                          Color[] colorTable) {
        ArrayList<PngChunkData> chunks = new ArrayList<>();

        // make DAT chunk
        MakeDatChunkResult mdr = makeDATchunk(imgData);

        // make IHDR
        int maxValue = 0;
        for (byte b : imgData) {
            if (maxValue < (b & 0xff)) maxValue = b & 0xff;
        }

        int bitDepth;
        if (maxValue < 2) bitDepth = 1;
        else if (maxValue < 4) bitDepth = 2;
        else if (maxValue < 16) bitDepth = 4;
        else if (maxValue < 256) bitDepth = 8;
        else bitDepth = 16;

        chunks.add(makeIHDR(oldIHDR, bitDepth, mdr.filterMethod, oldIHDR.getInterlaceMethod()));


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
        chunks.add(mdr.dataChunk);

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
    private MakeDatChunkResult makeDATchunk(byte[] imgData) {
        MakeDatChunkResult result = new MakeDatChunkResult();
        byte[] buf = new byte[imgData.length];
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
        result.dataChunk = new PngChunkData(chunkDat, CODE_IDAT);
        result.filterMethod = 0;
        return result;
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
     * brotli compress image data
     */
    private int brotliCompress(byte[] imgData, byte[] outBuf) {
        BrotliCompressor compressor = new BrotliCompressor();
        Brotli.Parameter parameter = new Brotli.Parameter();
        //parameter.set
        //compressor.compress()
        //deflater.setInput(imgData);
        //deflater.finish();
        //int zlib = deflater.deflate(outBuf);
        //deflater.end();
        //return zlib;
        return 0;
    }

    /**
     * zopfli compress image data
     */
    private int zopfliCompress(byte[] imgData, byte[] outBuf) {
        Zopfli zopfli = new Zopfli(32768);
        Options options = new Options(Options.OutputFormat.ZLIB, Options.BlockSplitting.FIRST, 60);

        Buffer out = zopfli.compress(options, imgData);
        int size = out.getSize() > outBuf.length ? outBuf.length : out.getSize();
        System.arraycopy(out.getData(), 0, outBuf, 0, size);
        return size;
    }

    private static class MakeDatChunkResult {
        PngChunkData dataChunk;
        int filterMethod;
    }
}
