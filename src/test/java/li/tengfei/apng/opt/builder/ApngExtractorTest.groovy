package li.tengfei.apng.opt.builder

import org.testng.annotations.Test

/**
 * ApngExtractor Test
 * @author ltf
 * @since 16/12/7, 下午6:17
 */
class ApngExtractorTest {

    @Test
    void testExtract() {
        String apngFile = getClass().getResource("/pngs/diamondstar-apngasm.png").path
        ApngExtractor extractor = new ApngExtractor()
        extractor.extract(apngFile)

        apngFile = getClass().getResource("/pngs/diamondstar-apngasm_frames_opt/diamondstar-apngasm_opt_0009.png").path
        PngReader reader = new PngReader(apngFile)
        reader.pngData.chunks.each { println(ChunkTypeHelper.getTypeName(it.typeCode)) }

    }
}
