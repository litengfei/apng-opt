package li.tengfei.apng.opt.builder;

import li.tengfei.apng.base.*;
import li.tengfei.apng.ext.ByteArrayPngChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.zip.CRC32;

import static li.tengfei.apng.base.ApngConst.*;
import static li.tengfei.apng.base.PngStream.intToArray;

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

    private ArrayList<byte[]> mFctlDatas = new ArrayList<>();

    private byte[] mActlData;

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
     * firstFrame: IHDR - acTL - PLTE and others - fcTL - IDAT(s)
     * otherFrame: fcTL - PLTE and others - IDAT(s)
     * png end   : IEND
     */
    private boolean compileToANG(OutputStream os) throws IOException {
        PngData firstFrame = mFrameDatas.get(0);
        byte[] firstFctl = mFctlDatas.get(0);
        if (firstFrame.chunks.get(0).typeCode != CODE_IHDR) return false;
        // sig
        os.write(PngStream.PNG_SIG_DAT);

        //  first IHDR
        os.write(mFrameDatas.get(0).chunks.get(0).data);

        // write acTL
        os.write(mActlData);

        // write other chunks in first frame
        boolean fctlWritten = false;
        for (int i = 1; i < firstFrame.chunks.size(); i++) {
            PngChunkData chunk = firstFrame.chunks.get(i);
            // write first fctl just before first IDAT
            if (chunk.typeCode == CODE_IDAT && !fctlWritten) {
                os.write(firstFctl);
                fctlWritten = true;
            }
            os.write(chunk.data);
        }

        // write other frames, fcTL is the first chunk
        for (int i = 1; i < mFrameDatas.size(); i++) {
            // first write fctl in [not first] frame
            os.write(mFctlDatas.get(i));
            // then write other chunks
            PngData frame = mFrameDatas.get(i);
            for (int j = 0; j < frame.chunks.size(); j++) {
                os.write(frame.chunks.get(j).data);
            }
        }

        // end
        os.write(PngStream.PNG_IEND_DAT);
        os.flush();
        return true;
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
        HashSet<PngChunkData> removeDatChunks = new HashSet<>();
        for (int i = mFrameDatas.size() - 1; i > 0; i--) {
            PngData cur = mFrameDatas.get(i);
            PngData pre = mFrameDatas.get(i - 1);

            // no removed dat chunks first
            removeDatChunks.clear();

            for (int j = cur.chunks.size() - 1; j >= 0; j--) {
                PngChunkData curChunk = cur.chunks.get(j);
                boolean isInherited = false;
                for (PngChunkData preChunk : pre.chunks) {
                    if (curChunk.equals(preChunk)) {
                        if (curChunk.typeCode == CODE_IDAT) {
                            // record current dat chunk could be removed,
                            // but don't remove it now
                            // [only can be remove when all DATs can be removed,
                            // and there are no other chunks in current frame]
                            removeDatChunks.add(curChunk);
                        } else {
                            isInherited = true;
                        }
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

            // all DATs must be removed together if :
            // 1. all DATs can be removed;
            // 2. there are no other chunks in current frame;
            // 3. fcTL is equals to previous one [exclude seq_num\delay_num\delay_den section]
            boolean datCanBeRemoved = PngChunkData.fctlEquals(mFctlDatas.get(i), mFctlDatas.get(i - 1));
            if (datCanBeRemoved) {
                for (PngChunkData chunk : cur.chunks) {
                    if (!removeDatChunks.contains(chunk)) {
                        datCanBeRemoved = false;
                        break;
                    }
                }
            }

            if (datCanBeRemoved) {
                // remove current frame, and add current frame's time to previous frame
                decActlFramesCount();       // decrease actl's frames_count
                mFrameDatas.remove(i);      // remove current frame
                incFctlDelay(mFctlDatas.get(i), mFctlDatas.get(i - 1)); // inc cur frames delay to previous one
                mFctlDatas.remove(i);       // remove current fctl
                System.out.println(i);
            }
        }
    }

    /**
     * decrease Frames Count In Actl
     */
    private void decActlFramesCount() {
        ApngACTLChunk actlChunk = new ApngACTLChunk();
        actlChunk.parse(new ByteArrayPngChunk(mActlData));
        intToArray(actlChunk.getNumFrames() - 1, mActlData, 8);
    }

    /**
     * merge current fctl's delay to previous one
     *
     * @param curFctlData the one would be deleted
     * @param preFctlData the previous one add current one's delay
     */
    private void incFctlDelay(byte[] curFctlData, byte[] preFctlData) {
        ApngFCTLChunk pre = new ApngFCTLChunk();
        pre.parse(new ByteArrayPngChunk(preFctlData));
        ApngFCTLChunk cur = new ApngFCTLChunk();
        cur.parse(new ByteArrayPngChunk(curFctlData));

        int num = pre.getDelayNum();
        int den = pre.getDelayDen();
        if (den == cur.getDelayDen()) {
            num += cur.getDelayNum();
        } else {
            num = num * cur.getDelayDen() + den * cur.getDelayNum();
            den = den * cur.getDelayDen();
            while (num % 2 == 0 && den % 2 == 0) {
                num /= 2;
                den /= 2;
            }
        }

        int num_den = ((num & 0xFFFF) << 16) | (den & 0xFFFF);
        intToArray(num_den, preFctlData, 28);
        CRC32 crc32 = new CRC32();
        crc32.update(preFctlData, 4, preFctlData.length - 8);
        intToArray((int) crc32.getValue(), preFctlData, preFctlData.length - 4);
    }

    /**
     * prepare for rebuild, check frames and pngs count and size
     *
     * @return true if success, false if failed
     */
    private boolean prepare() {
        // collect fctls
        mFctlDatas.clear();
        mActlData = null;
        ArrayList<ApngFCTLChunk> fctlChunks = new ArrayList<>();
        for (PngChunkData chunk : mApngData.chunks) {
            if (chunk.typeCode == CODE_fcTL) {
                ApngFCTLChunk fctlChunk = new ApngFCTLChunk();
                fctlChunk.parse(new ByteArrayPngChunk(chunk.data));
                fctlChunks.add(fctlChunk);
                mFctlDatas.add(chunk.data);
            } else if (chunk.typeCode == CODE_acTL) {
                mActlData = chunk.data;
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
