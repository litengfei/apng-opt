package li.tengfei.apng.opt.builder;

import java.util.ArrayList;

/**
 * Ang Data, contain ang chunks
 *
 * @author ltf
 * @since 16/12/11, 下午2:33
 */
public class AngData {
    private ArrayList<AngChunkData> chunks = new ArrayList<>();

    public ArrayList<AngChunkData> getChunks() {
        return chunks;
    }

    public void addChunk(PngChunkData chunkData, int frameIndex) {
        AngChunkData angChunk = new AngChunkData(chunkData, frameIndex);
        chunks.add(angChunk);
    }

    public void addChunk(AngChunkData chunkData) {
        addChunk(chunkData, chunkData.getFrameIndex());
    }
}
