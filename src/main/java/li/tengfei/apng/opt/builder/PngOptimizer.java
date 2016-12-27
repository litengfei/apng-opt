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
            Map<Color, Color> map = reducer.reduce(image.getPixels(), 256);
            Color[] colorTable = new Color[map.values().size()];
            map.values().toArray(colorTable);
            HashMap<Color, Integer> colorIndex = new HashMap<>(colorTable.length);
            for (int i = 0; i < colorTable.length; i++) {
                colorIndex.put(colorTable[i], i);
            }
            byte[] data = new byte[image.getPixels().length];
            int i = 0;
            for (Color color : image.getPixels()) {
                data[i++] = (byte) (colorIndex.get(map.get(color)) & 0xff);
            }
            ApngIHDRChunk ihdr = new ApngIHDRChunk();
            ihdr.parse(new ByteArrayPngChunk(chunks.get(image.getIhdrIndex()).getData()));

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
