package li.tengfei.apng.opt.builder

import org.testng.annotations.Test

import static li.tengfei.apng.opt.shrinker.TestConst.WORK_DIR

/**
 *
 * @author ltf
 * @since 16/12/26, 上午11:36
 */
class PngOptimizerTest {
    @Test
    void testOptimize() {

        //String apngFile = getClass().getResource("/pngs/suite/f00n0g08.png").path
        String apngFile = getClass().getResource("/pngs/suite/f00n2c08.png").path
//        String apngFile = getClass().getResource("/pngs/diamondstar-orig-182.png").path
        //String apngFile = getClass().getResource("/pngs/diamondstar-tiny-182.png").path
        String outFile = WORK_DIR + "/diamondstar-182.png"
        PngOptimizer optimizer = new PngOptimizer()
        optimizer.optimize(apngFile, outFile)

//        toFile(optimizer.tmpTinyDAT, WORK_DIR + "/tiny.dat")
//        toFile(optimizer.tmpLtfDAT, WORK_DIR + "/ltf.dat")
    }


    void toFile(byte[] dat, String fn) {
        FileOutputStream fos = new FileOutputStream(fn)
        fos.write(dat)
        fos.flush()
    }
}
