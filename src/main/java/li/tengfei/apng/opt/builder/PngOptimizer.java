package li.tengfei.apng.opt.builder;

import li.tengfei.apng.base.ApngIHDRChunk;
import li.tengfei.apng.base.FormatNotSupportException;
import li.tengfei.apng.ext.ByteArrayPngChunk;
import li.tengfei.apng.ext.PngImage;
import li.tengfei.apng.ext.PngImageDecoder;
import li.tengfei.apng.ext.PngImageEncoder;
import li.tengfei.apng.opt.optimizer.*;
import li.tengfei.apng.opt.optimizer.ColorMapper.Mapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
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
    private ColorMapper mapper = new ColorSimuMapper();
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
            Map<Color, Color> colorMap = reducer.reduce(image.getPixels(), 256);

            // compute color index in color table
            ApngIHDRChunk ihdr = new ApngIHDRChunk();
            ihdr.parse(new ByteArrayPngChunk(chunks.get(image.getIhdrIndex()).getData()));

            Mapping pixelsMap = mapper.mapping(image.getPixels(), ihdr.getHeight(), colorMap);
            // make image chunks
            newChunks.addAll(encoder.encode(pixelsMap.pixelIndexes, ihdr.getHeight(), pixelsMap.colorTable));
        }
        return newChunks;
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
