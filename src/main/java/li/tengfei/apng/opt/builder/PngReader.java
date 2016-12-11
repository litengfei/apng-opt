package li.tengfei.apng.opt.builder;

import li.tengfei.apng.base.ApngConst;
import li.tengfei.apng.base.ApngMmapParserChunk;
import li.tengfei.apng.base.FormatNotSupportException;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

/**
 * Png Reader for rebuild an apng
 *
 * @author ltf
 * @since 16/12/6, 下午4:48
 */
public class PngReader {
    private MappedByteBuffer mBuffer;
    private ApngMmapParserChunk mChunk;


    public PngReader(String apngFile) throws IOException, FormatNotSupportException {
        this(new File(apngFile));
    }

    public PngReader(File apngFile) throws IOException, FormatNotSupportException {
        RandomAccessFile f = new RandomAccessFile(apngFile, "r");
        mBuffer = f.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, f.length());
        f.close();
        if (mBuffer.getInt() != ApngConst.PNG_SIG
                && mBuffer.getInt(4) != ApngConst.PNG_SIG_VER
                && mBuffer.getInt(8) != ApngConst.CODE_IHDR) {
            throw new FormatNotSupportException("Not a png/apng file");
        }
    }

    /**
     * get chunks list of a png file
     */
    public void getChunks(ArrayList<PngChunkData> chunks) throws IOException {
        mChunk = new ApngMmapParserChunk(mBuffer);
        // locate IHDR
        mChunk.parsePrepare(8);

        while (mChunk.parseNext() > 0) {
            chunks.add(new PngChunkData(
                    mChunk.duplicateData(),
                    mChunk.getTypeCode()
            ));
        }
    }

    /**
     * get png data of a png file
     */
    public PngData getPngData() throws IOException {
        PngData pngData = new PngData();
        getChunks(pngData.chunks);
        return pngData;
    }
}
