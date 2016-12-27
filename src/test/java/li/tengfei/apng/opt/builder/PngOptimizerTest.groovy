package li.tengfei.apng.opt.builder

import org.testng.annotations.Test

import static li.tengfei.apng.opt.shrinker.TestConst.getWORK_DIR

/**
 *
 * @author ltf
 * @since 16/12/26, 上午11:36
 */
class PngOptimizerTest {
    @Test
    void testOptimize() {
        //String apngFile = getClass().getResource("/pngs/diamondstar-orig-182.png").path
        String apngFile = getClass().getResource("/pngs/diamondstar-tiny-182.png").path
        String outFile = WORK_DIR + "/diamondstar-182.png"
        PngOptimizer optimizer = new PngOptimizer()
        optimizer.optimize(apngFile, outFile);

    }
}
