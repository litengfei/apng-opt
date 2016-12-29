package li.tengfei.apng.opt.builder;

import li.tengfei.apng.base.ApngIHDRChunk;
import li.tengfei.apng.base.FormatNotSupportException;
import li.tengfei.apng.ext.ByteArrayPngChunk;
import li.tengfei.apng.ext.PngImage;
import li.tengfei.apng.ext.PngImageDecoder;
import li.tengfei.apng.ext.PngImageEncoder;
import li.tengfei.apng.opt.optimizer.KMeansReducer;
import li.tengfei.apng.opt.optimizer.MedianCutReducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.zip.DataFormatException;

import static li.tengfei.apng.base.PngStream.PNG_IEND_DAT;
import static li.tengfei.apng.base.PngStream.PNG_SIG_DAT;

/**
 * png size optimizer
 *
 * @author ltf
 * @since 16/12/26, 上午10:13
 */
public class PngOptimizer {
    private static final Logger log = LoggerFactory.getLogger(PngOptimizer.class);
    private PngImageDecoder decoder = new PngImageDecoder();
    private PngImageEncoder encoder = new PngImageEncoder();
    private PngData mPng;

    private ArrayList<PngData> mFrameDatas;

    private ArrayList<PngChunkData> mFctlChunks = new ArrayList<>();

    private PngChunkData mActlChunk;

    public boolean optimize(String srcApngFile, String outFile) {
        try {
            mPng = new PngReader(srcApngFile).getPngData();
            //
            ArrayList<PngChunkData> chunks = colorReduce(mPng.chunks);
            saveToPng(new FileOutputStream(outFile), chunks);
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
    private ArrayList<PngChunkData> colorReduce(ArrayList<PngChunkData> chunks) throws DataFormatException {

        ArrayList<PngImage> images = decoder.decodeImages(chunks);
        KMeansReducer reducer = new KMeansReducer();
        reducer.setInitReducer(new MedianCutReducer());

        ArrayList<PngChunkData> newChunks = new ArrayList<>();

        for (PngImage image : images) {
            // compute color table
            Map<Color, Color> map = reducer.reduce(image.getPixels(), 256);
            HashSet<Color> set = new HashSet<>(256);
            set.addAll(map.values());
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
            ihdr.parse(new ByteArrayPngChunk(chunks.get(image.getIhdrIndex()).getData()));

            // compute color index in color table
            HashMap<Color, Integer> colorIndex = new HashMap<>(colorTable.length);
            for (int i = 0; i < colorTable.length; i++) {
                colorIndex.put(colorTable[i], i);
            }

            // update pixels color indexes
            byte[] data = new byte[image.getPixels().length + ihdr.getHeight()];
            int i = 0;
            for (Color color : image.getPixels()) {
                if (i % (ihdr.getWidth() + 1) == 0) {
                    data[i++] = 0;
                }
                data[i++] = (byte) (colorIndex.get(map.get(color)) & 0xff);
            }

            // make image chunks
            newChunks.addAll(encoder.encode(ihdr, data, colorTable));
        }

        return newChunks;

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

    private boolean saveToPng(OutputStream os, ArrayList<PngChunkData> chunks) throws IOException {
        // sig
        os.write(PNG_SIG_DAT);
        // write out all chunk
        for (PngChunkData angChunk : chunks) os.write(angChunk.data);
        // end
        os.write(PNG_IEND_DAT);
        os.flush();
        return true;
    }
}
