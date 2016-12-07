package li.tengfei.apng.opt.builder

import org.testng.annotations.Test

/**
 * RebuildApngReader Test
 * @author ltf
 * @since 16/12/7, 下午12:24
 */
class PngReaderTest {


    @Test
    void testGetChunks() {
        String apngFile = getClass().getResource("/pngs/diamondstar-apngasm.png").path
        PngReader reader = new PngReader(apngFile)
        reader.chunks.each { println(ChunkTypeHelper.getTypeName(it.typeCode)) }
    }

    @Test
    void testGetPngData() {
        String apngFile = getClass().getResource("/pngs/diamondstar-apngasm/diomand_tiny_00055.png").path
        PngReader reader = new PngReader(apngFile)
        reader.pngData.chunks.each { println(ChunkTypeHelper.getTypeName(it.typeCode)) }
    }
}
