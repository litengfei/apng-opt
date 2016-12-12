package li.tengfei.apng.opt.optimizer;

import li.tengfei.apng.base.PngStream;
import li.tengfei.apng.ext.DIntWriter;
import li.tengfei.apng.opt.builder.AngChunkData;
import li.tengfei.apng.opt.builder.AngData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.CRC32;

import static li.tengfei.apng.base.ApngConst.*;
import static li.tengfei.apng.base.DInt.*;

/**
 * Use patch chunk to reduce file size
 *
 * @author ltf
 * @since 16/12/11, 下午2:17
 */
public class PatchOptimizer implements AngOptimizer {
    private static final Logger log = LoggerFactory.getLogger(PatchOptimizer.class);
    private CRC32 crc = new CRC32();


    /**
     * DInt bytes cost for value v
     */
    private static int dIntCost(int v) {
        if (v <= MAX_1_BYTE_DINT_VALUE) return 1;
        else if (v <= MAX_2_BYTE_DINT_VALUE) return 2;
        else if (v <= MAX_3_BYTE_DINT_VALUE) return 3;
        else if (v <= MAX_4_BYTE_DINT_VALUE) return 4;

        throw new IllegalStateException("not supported DInt value");
    }

    @Override
    public AngData optimize(AngData ang) {
        List<PatchItem> patches = genPatchs(ang);

//        List<Integer> delChunkIndexes = new ArrayList<>();
//        patches = mergePatches(patches, delChunkIndexes);


        return applyPatches(ang, patches);
    }


    private AngData applyPatches(AngData ang, List<PatchItem> patches) {
        AngData optAng = new AngData();

        int chunkIdx = 0;
        int patchIdx = 0;
        int allPatchSize = 0;
        int allOrigSize = 0;
        List<PatchItem> framePatches = new ArrayList<>();
        for (AngChunkData chunk : ang.getChunks()) {
            if ((chunk.getTypeCode() == CODE_IDAT || chunk.getTypeCode() == CODE_fdAT) && framePatches.size() > 0) {
                // combine to a patch chunk
                int defSize = 0;
                int dataSize = 0;
                int hashSize = 0;

                for (PatchItem patch : framePatches) {
                    defSize += patch.defData.length;
                    dataSize += patch.data.length;
                    hashSize += dIntCost(patch.typeHash);
                }
                int headersLen = hashSize + defSize;
                int chunkBodySize = dIntCost(headersLen) + headersLen + dataSize;
                byte[] data = new byte[4 + 4 + chunkBodySize + 4];

                // write chunk bodySize
                PngStream.intToArray(chunkBodySize, data, 0);
                // write chunk typeCode
                PngStream.intToArray(CODE_paCH, data, 4);

                DIntWriter dInt = new DIntWriter();
                int offset = 8;
                // write HeadersLen
                dInt.setValue(headersLen);
                offset += dInt.write(data, offset);

                // write all Headers
                for (PatchItem patch : framePatches) {
                    // write typeHash
                    dInt.setValue(patch.typeHash);
                    offset += dInt.write(data, offset);
                    // write defs data
                    System.arraycopy(patch.defData, 0, data, offset, patch.defData.length);
                    offset += patch.defData.length;
                }

                // write all datas
                for (PatchItem patch : framePatches) {
                    // write data
                    System.arraycopy(patch.data, 0, data, offset, patch.data.length);
                    offset += patch.data.length;
                }

                // write chunk CRC
                crc.reset();
                crc.update(data, 4, 4 + chunkBodySize);
                PngStream.intToArray((int) crc.getValue(), data, offset);

                AngChunkData patchChunk = new AngChunkData(data, CODE_paCH, chunk.getFrameIndex());
                optAng.addChunk(patchChunk);
                framePatches.clear();
                allPatchSize += data.length;
            }

            if (patchIdx >= patches.size() || chunkIdx < patches.get(patchIdx).chunkIndex) {
                optAng.addChunk(chunk);
            } else {
                framePatches.add(patches.get(patchIdx));
                allOrigSize += chunk.getData().length;
                patchIdx++;
            }

            chunkIdx++;
        }

        log.debug(String.format("PatchOptimize Result:  OrigSize: %d, PatchSize: %d, saved: %d",
                allOrigSize, allPatchSize, allPatchSize - allOrigSize));

        return optAng;
    }


//    /**
//     * merge same type chunk patch in one frame
//     */
//    private List<PatchItem> mergePatches(List<PatchItem> patches, List<Integer> delChunkIndexes) {
//        ArrayList<PatchItem> optPatches = new ArrayList<>();
//
//
//
//        return optPatches;
//    }

    /**
     * generate patches for the ang
     */
    private List<PatchItem> genPatchs(AngData ang) {
        HashMap<Integer, AngChunkData> existsChunks = new HashMap<>();
        ArrayList<PatchItem> patchItems = new ArrayList<>();

        for (int i = 0; i < ang.getChunks().size(); i++) {
            AngChunkData chunk = ang.getChunks().get(i);
            if (!ChunkTypeHelper.canUsePatch(chunk.getTypeCode())) continue;

            AngChunkData pre = existsChunks.get(chunk.getTypeCode());
            if (pre != null) {
                PatchItem patchItem = calculatePatch(pre.getData(), chunk.getData(), chunk.getTypeCode() == CODE_IHDR);
                if (patchItem != null) {
                    patchItem.chunkIndex = i;
                    patchItem.frameIndex = chunk.getFrameIndex();
                    patchItem.typeCode = chunk.getTypeCode();
                    patchItem.typeHash = ChunkTypeHelper.getTypeHash(patchItem.typeCode, existsChunks.keySet());


                    patchItems.add(patchItem);
                }
            }
            // update chunk status to current
            existsChunks.put(chunk.getTypeCode(), chunk);
        }
        return patchItems;
    }

    /**
     * calculate a patch for previous chunk and current chunk
     */
    private PatchItem calculatePatch(final byte[] preData, final byte[] curData, boolean isIHDR) {
        ArrayList<PatchItemBlock> blocks = new ArrayList<>();

        // get all different data blocks
        int lastDiffPos = -1; // <0 means not init
        for (int i = 0; i < curData.length; i++) {
            if ((i < preData.length && curData[i] == preData[i])  // normal chunk
                    ||
                    (isIHDR && ((i >= 8 && i < 16) || (i >= 21 && i < 25)))) {            // IHDR chunk
                // same data
                if (lastDiffPos >= 0) {
                    blocks.add(new PatchItemBlock(lastDiffPos, i - lastDiffPos));
                    lastDiffPos = -1;
                }
            } else {
                // different data
                if (lastDiffPos < 0) lastDiffPos = i;
            }
        }
        if (lastDiffPos >= 0) {
            blocks.add(new PatchItemBlock(lastDiffPos, curData.length - lastDiffPos));
        }

        // add delete block if data size reduced
        if (curData.length < preData.length) {
            int delSize = preData.length - curData.length;
            while (delSize > 0) {
                int ds = delSize < 65535 ? delSize : 65535;
                delSize -= ds;
                blocks.add(new PatchItemBlock(curData.length, 2).setDeletePatch(ds));
            }
        }

        if (blocks.size() == 0)
            throw new IllegalStateException("Why are same data chunks appears? is processInherit() not run?");

        // optimize blocks
        blocks = optimizeBlocks(blocks);

        // prepare data by blocks definition
        int defSize = 0;
        int dataSize = 0;
        int itemCount = 0;
        for (PatchItemBlock block : blocks) {
            dataSize += block.dataSize;
            defSize += block.costBytesCount - block.dataSize;
            itemCount++;
        }

        int noHashCost = dataSize + defSize + dIntCost(itemCount);

        // select use patch or not conditions
        if (noHashCost >= curData.length - 13) return null;
//        log.debug(String.format("noHashCost: %d, curDataLength: %d, saved: %d",
//                noHashCost, curData.length, noHashCost - curData.length));

        PatchItem patchItem = new PatchItem();
        patchItem.defData = new byte[defSize + dIntCost(itemCount)];
        patchItem.data = new byte[dataSize];
        int defOff = 0;
        int dataOff = 0;
        DIntWriter dInt = new DIntWriter();
        // write ItemsCount first
        dInt.setValue(itemCount);
        defOff += dInt.write(patchItem.defData, defOff);
        for (PatchItemBlock block : blocks) {
            // write def : dstOffset
            dInt.setValue(block.dstOffset);
            defOff += dInt.write(patchItem.defData, defOff);

            if (block.isDeletePatch) {
                // write def : dataSize == 0 for DELETE patch
                dInt.setValue(0);
                defOff += dInt.write(patchItem.defData, defOff);
                // write data, 2 byte BIG-ENDIAN unsigned word to specified bytes count to delete
                patchItem.data[dataOff] = (byte) (block.deleteSize >> 8 & 0xFF);
                patchItem.data[dataOff + 1] = (byte) (block.deleteSize & 0xFF);

//                log.debug(String.format("dstOff: %d, delSize: %d, srcOff: %d",
//                        block.dstOffset, block.deleteSize, dataOff));
                dataOff += 2;
            } else {
                // write def : dataSize
                dInt.setValue(block.dataSize);
                defOff += dInt.write(patchItem.defData, defOff);
                // write data
                System.arraycopy(curData, block.dstOffset, patchItem.data, dataOff, block.dataSize);

//                log.debug(String.format("dstOff: %d, dataSize: %d, srcOff: %d",
//                        block.dstOffset, block.dataSize, dataOff));

                dataOff += block.dataSize;
            }
        }
        return patchItem;
    }

    /**
     * optimize patch blocks for size and performance
     */
    private ArrayList<PatchItemBlock> optimizeBlocks(ArrayList<PatchItemBlock> blocks) {
        ArrayList<PatchItemBlock> optBlocks = new ArrayList<>();
        PatchItemBlock preBlock = null;
        for (PatchItemBlock block : blocks) {
            if (block.isDeletePatch) {
                if (preBlock != null) {
                    optBlocks.add(preBlock);
                    preBlock = null;
                }
                optBlocks.add(block);
                continue;
            }

            if (preBlock == null) {
                preBlock = block;
            } else {
                int newSize = block.dstOffset - preBlock.dstOffset + block.dataSize;
                int newCost = PatchItemBlock.calBlockCost(preBlock.dstOffset, newSize);
                if (newCost <= preBlock.costBytesCount + block.costBytesCount) {
                    preBlock = new PatchItemBlock(preBlock.dstOffset, newSize, newCost);
                } else {
                    optBlocks.add(preBlock);
                    preBlock = block;
                }
            }
        }

        if (preBlock != null) {
            optBlocks.add(preBlock);
        }

        return optBlocks;
    }

    private static class PatchItem {
        int chunkIndex;
        int frameIndex;
        int typeCode;
        int typeHash;

        byte[] defData; // definition data as Items in patch Header
        byte[] data; // raw data to copy
    }

    private static class PatchItemBlock {
        private int dstOffset;
        private int dataSize;
        private boolean isDeletePatch;
        private int deleteSize;
        private int costBytesCount; // all bytes count to implement this patch block

        PatchItemBlock(int dstOffset, int dataSize) {
            this.dstOffset = dstOffset;
            this.dataSize = dataSize;
            this.costBytesCount = calBlockCost(dstOffset, dataSize);
        }

        PatchItemBlock(int dstOffset, int dataSize, int costBytesCount) {
            this.dstOffset = dstOffset;
            this.dataSize = dataSize;
            this.costBytesCount = costBytesCount;
        }

        /**
         * calculate bytes cost for PatchItemBlock
         */
        static int calBlockCost(int offset, int dataSize) {
            return dIntCost(offset) + dIntCost(dataSize) + dataSize;
        }

        PatchItemBlock setDeletePatch(int deleteSize) {
            isDeletePatch = true;
            dataSize = 2;
            this.costBytesCount = calBlockCost(dstOffset, 2);
            this.deleteSize = deleteSize;
            return this;
        }
    }
}