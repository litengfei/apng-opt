package li.tengfei.apng.opt.builder;

import li.tengfei.apng.base.ApngFCTLChunk;
import li.tengfei.apng.base.ApngIHDRChunk;
import li.tengfei.apng.base.FormatNotSupportException;
import li.tengfei.apng.base.PngStream;
import li.tengfei.apng.ext.ByteArrayPngChunk;
import li.tengfei.apng.ext.DIntWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;

import static li.tengfei.apng.base.ApngConst.*;

/**
 * Apng Rebuilder
 *
 * @author ltf
 * @since 16/12/6, 下午4:33
 */
public class ApngRebuilder {

    private static final Logger log = LoggerFactory.getLogger(ApngRebuilder.class);

    private PngData mApngData;

    private ArrayList<PngData> mFrameDatas;

    private ArrayList<PngChunkData> mFctlChunks = new ArrayList<>();

    private PngChunkData mActlChunk;

    public PngData getApngData() {
        return mApngData;
    }

    public ArrayList<PngData> getFrameData() {
        return mFrameDatas;
    }

    public boolean rebuild(String srcApngFile, String shrinkedImgsDir, String outFile) {
        try {
            mApngData = new PngReader(srcApngFile).getPngData();
            mFrameDatas = new PngsCollector().getPngs(shrinkedImgsDir);

            // prepare inputs
            if (!prepare()) return false;

            // remove no need inherited chunks
            processInherit();

            // compile .ang output
            return compileToANG(new BufferedOutputStream(new FileOutputStream(outFile)));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (FormatNotSupportException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * compile to ".ang" format [ No Portable APNG ]
     * Format:
     * png header: SIG
     * ang chunks list
     * png end   : IEND
     */
    private boolean compileToANG(OutputStream os) throws IOException {
        // sig
        os.write(PngStream.PNG_SIG_DAT);

        ArrayList<PngChunkData> angChunks = compileAngChunks();

        // write out all chunk
        for (PngChunkData angChunk : angChunks) os.write(angChunk.data);
        // end
        os.write(PngStream.PNG_IEND_DAT);
        os.flush();
        return true;
    }

    /**
     * compile to ".ang" format ordered chunks list
     * Chunks Order Layout:
     * firstFrame: IHDR - acTL - PLTE and others - fcTL - IDAT(s)
     * otherFrame: fcTL - PLTE and others - IDAT(s)
     */
    private ArrayList<PngChunkData> compileAngChunks() {
        ArrayList<PngChunkData> angChunks = new ArrayList<>();
        PngData firstFrame = mFrameDatas.get(0);
        PngChunkData firstFctl = mFctlChunks.get(0);
        if (firstFrame.chunks.get(0).typeCode != CODE_IHDR) return angChunks;

        //  first IHDR
        angChunks.add(mFrameDatas.get(0).chunks.get(0));

        // write acTL
        angChunks.add(mActlChunk);

        // write other chunks in first frame
        boolean fctlWritten = false;
        for (int i = 1; i < firstFrame.chunks.size(); i++) {
            PngChunkData chunk = firstFrame.chunks.get(i);
            // write first fctl just before first IDAT
            if (chunk.typeCode == CODE_IDAT && !fctlWritten) {
                angChunks.add(firstFctl);
                fctlWritten = true;
            }
            angChunks.add(chunk);
        }

        // write other frames, fcTL is the first chunk
        for (int i = 1; i < mFrameDatas.size(); i++) {
            // first write fctl in [not first] frame
            angChunks.add(mFctlChunks.get(i));
            // then write other chunks
            PngData frame = mFrameDatas.get(i);
            for (int j = 0; j < frame.chunks.size(); j++) {
                angChunks.add(frame.chunks.get(j));
            }
        }

        return angChunks;
    }


//    /**
//     * merge each frame's IDATs or fcATs to only one,
//     * and compute each new fdAT translate to IDAT's CRC
//     *
//     * @return full fcRC chunk data (size + codeType + data + crc)
//     */
//    private byte[] mergeDatAndGetFcrc() {
//        byte[] fcRCdata = new byte[mFrameDatas.size() * 4 + 12];
//        CRC32 crcCal = new CRC32();
//
//        for (int i = 0; i < mFrameDatas.size(); i++) {
//            crcCal.re
//
//        }
//
//        return fcRCdata;
//    }

    /**
     * process reuse optimize,
     * remove same chunks if the previous frame contains
     */
    @Deprecated
    private void processReuse() {
        //Arrays.equals()
        for (int i = 0; i < mFrameDatas.size(); i++) {
            PngData leader = mFrameDatas.get(i);
            for (int j = i + 2; j < mFrameDatas.size(); j++) {
                PngData follower = mFrameDatas.get(j);

                for (int x = follower.chunks.size() - 1; x >= 0; x--) {
                    PngChunkData followChunk = follower.chunks.get(x);
                    boolean isReused = false;
                    for (PngChunkData leaderChunk : leader.chunks) {
                        if (followChunk.equals(leaderChunk)) {
                            isReused = true;
                            break;
                        }
                    }
                    if (isReused) {
                        follower.chunks.remove(x);
                        log.info(String.format("reused chunk removed: frame[%d].%s",
                                j,
                                ChunkTypeHelper.getTypeName(followChunk.typeCode)
                        ));
                    }
                }
            }

        }
    }

    /**
     * process inherit optimize,
     * remove same chunks if the previous frame contains
     */
    private void processInherit() {
        //Arrays.equals()
        for (int i = mFrameDatas.size() - 1; i > 0; i--) {
            PngData cur = mFrameDatas.get(i);
            PngData pre = mFrameDatas.get(i - 1);

            for (int j = cur.chunks.size() - 1; j >= 0; j--) {
                PngChunkData curChunk = cur.chunks.get(j);
                boolean isInherited = false;
                for (PngChunkData preChunk : pre.chunks) {
                    if (curChunk.equals(preChunk)) {
                        isInherited = true;
                        break;
                    }
                }
                if (isInherited) {
                    cur.chunks.remove(j);
//                    log.info(String.format("inherit chunk removed: frame[%d].%s",
//                            i,
//                            ChunkTypeHelper.getTypeName(curChunk.typeCode)
//                    ));
                }
            }
        }
    }

    /**
     * prepare for rebuild, check frames and pngs count and size
     *
     * @return true if success, false if failed
     */
    private boolean prepare() {
        // collect fctls
        mFctlChunks.clear();
        mActlChunk = null;
        ArrayList<ApngFCTLChunk> fctlChunks = new ArrayList<>();
        for (PngChunkData chunk : mApngData.chunks) {
            if (chunk.typeCode == CODE_fcTL) {
                ApngFCTLChunk fctlChunk = new ApngFCTLChunk();
                fctlChunk.parse(new ByteArrayPngChunk(chunk.data));
                fctlChunks.add(fctlChunk);
                mFctlChunks.add(chunk);
            } else if (chunk.typeCode == CODE_acTL) {
                mActlChunk = chunk;
            }
        }

        // check all frames count and pngs count
        if (fctlChunks.size() != mFrameDatas.size()) {
            log.error("apng frames count not equals to png pictures count");
            return false;
        }

        // collect ihdrs
        ArrayList<ApngIHDRChunk> ihdrChunks = new ArrayList<>();
        for (PngData frame : mFrameDatas) {
            for (PngChunkData chunk : frame.chunks) {
                if (chunk.typeCode == CODE_IHDR) {
                    ApngIHDRChunk ihdrChunk = new ApngIHDRChunk();
                    ihdrChunk.parse(new ByteArrayPngChunk(chunk.data));
                    ihdrChunks.add(ihdrChunk);
                    break;
                }
            }
        }

        // check fctls count and ihdrs count
        if (fctlChunks.size() != ihdrChunks.size()) {
            log.error("frames' fctl count not equals to pictures' ihdr count");
            return false;
        }

        // check each frame size and png size
        for (int i = 0; i < fctlChunks.size(); i++) {
            if (fctlChunks.get(i).getWidth() != ihdrChunks.get(i).getWidth() ||
                    fctlChunks.get(i).getHeight() != ihdrChunks.get(i).getHeight()) {

                log.error(String.format("frame and png have different size\n index: %d, fw: %d, fh: %d, pw: %d, ph: %d",
                        i,
                        fctlChunks.get(i).getWidth(),
                        fctlChunks.get(i).getHeight(),
                        ihdrChunks.get(i).getWidth(),
                        ihdrChunks.get(i).getHeight()
                ));
                return false;
            }
        }
        return true;
    }
}
