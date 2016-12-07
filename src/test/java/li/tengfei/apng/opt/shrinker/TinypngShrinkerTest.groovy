package li.tengfei.apng.opt.shrinker

import org.testng.annotations.Test

import static li.tengfei.apng.opt.shrinker.TestConst.WORK_DIR

/**
 * TinypngOptimizer Test
 * @author ltf
 * @since 16/12/6, 下午2:11
 */
class TinypngShrinkerTest {

    @Test
    void testOptimize() {
        def is = getClass().getResourceAsStream("/pngs/diamondstar-orig-088.png")
        def os = new FileOutputStream(WORK_DIR + '/diamondstar-orig-088-opt.png')
        new TinypngShrinker().optimize(is, os);
    }
}
