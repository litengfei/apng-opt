package li.tengfei.apng.opt.builder

import li.tengfei.apng.opt.optimizer.ChunkTypeHelper
import org.testng.annotations.Test

/**
 * ApngExtractor Test
 * @author ltf
 * @since 16/12/7, 下午6:17
 */
class ApngExtractorTest {

    @Test
    void testExtract() {
        extract("diamondstar-apngasm.png")
        extract("diamondstar-tiny.png")
        extract("car.png")
        extract("car-tiny.png")
        extract("diamondstar-ang.ang")
    }

    private void extract(String resFn) {
        String apngFile = getClass().getResource("/pngs/" + resFn).path
        ApngExtractor extractor = new ApngExtractor()
        extractor.extract(apngFile, null)
    }

    private void listChunks() {
        String apngFile = getClass().getResource("/pngs/diamondstar-apngasm_frames_opt/diamondstar-apngasm_opt_0009.png").path
        PngReader reader = new PngReader(apngFile)
        reader.pngData.chunks.each { println(ChunkTypeHelper.getTypeName(it.typeCode)) }
    }
}
