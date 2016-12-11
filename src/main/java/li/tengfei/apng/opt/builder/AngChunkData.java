package li.tengfei.apng.opt.builder;

/**
 * AngChunkData, contains frameIndex for each chunk
 *
 * @author ltf
 * @since 16/12/11, 下午2:43
 */
public class AngChunkData extends PngChunkData {
    private int frameIndex;

    public AngChunkData(PngChunkData pngChunk, int frameIndex) {
        super(pngChunk);
        this.frameIndex = frameIndex;
    }

    public AngChunkData(AngChunkData angChunkData) {
        this(angChunkData, angChunkData.frameIndex);
    }

    public AngChunkData(byte[] data, int typeCode, int frameIndex) {
        super(data, typeCode);
        this.frameIndex = frameIndex;
    }

    public int getFrameIndex() {
        return frameIndex;
    }
}
