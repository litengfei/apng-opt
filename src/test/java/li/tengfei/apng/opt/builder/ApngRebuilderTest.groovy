package li.tengfei.apng.opt.builder

import org.testng.annotations.Test

import static li.tengfei.apng.opt.shrinker.TestConst.WORK_DIR
import static org.testng.Assert.assertTrue

/**
 * ApngRebuilder Test
 * @author ltf
 * @since 16/12/6, 下午4:33
 */
class ApngRebuilderTest {

    @Test
    void testRebuild() throws Exception {
        String apngFile = getClass().getResource("/pngs/diamondstar-apngasm.png").path
        String shrinkedImgsDir = getClass().getResource("/pngs/diamondstar-apngasm_frames_opt").path
        String outFile = WORK_DIR + "/diamondstar-ang.ang"

        ApngRebuilder rebuilder = new ApngRebuilder()
        assertTrue(rebuilder.rebuild(apngFile, shrinkedImgsDir, outFile))

        ApngExtractor extractor = new ApngExtractor()
        extractor.extract(outFile)
    }
}
