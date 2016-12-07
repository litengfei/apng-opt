package li.tengfei.apng.opt.builder

import org.testng.annotations.Test

/**
 * RebuildApngReader Test
 * @author ltf
 * @since 16/12/7, 下午12:24
 */
class RebuildApngReaderTest {
    @Test
    void testGetChunks() {
        String apngFile = getClass().getResource("/pngs/diamondstar-apngasm.png").path

        RebuildApngReader reader = new RebuildApngReader(apngFile)
        reader.chunks.each { i ->
            println(ChunkTypeHelper.getTypeName(i.typeCode))
        }
    }
}
