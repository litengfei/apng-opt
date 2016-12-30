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

        String apngFile = getClass().getResource("/pngs/diamondstar-orig-182.png").path
        //String apngFile = getClass().getResource("/pngs/diamondstar-tiny-182.png").path
        String outFile = WORK_DIR + "/diamondstar-182.png"
        PngOptimizer optimizer = new PngOptimizer()
        optimizer.optimize(apngFile, outFile)

//        toFile(optimizer.tmpTinyDAT, WORK_DIR + "/tiny.dat")
//        toFile(optimizer.tmpLtfDAT, WORK_DIR + "/ltf.dat")
    }

    @Test
    void testOptimize_suites() {

//        opt_suite("f00n0g08.png")
//        opt_suite("f00n2c08.png")
//        opt_suite("f01n0g08.png")
//        opt_suite("f01n2c08.png")
//        opt_suite("f02n0g08.png")
//        opt_suite("f02n2c08.png")
//        opt_suite("f03n0g08.png")
//        opt_suite("f03n2c08.png")
//        opt_suite("f04n0g08.png")
//        opt_suite("f04n2c08.png")
        opt_suite("f99n0g04.png")


    }

    void opt_suite(String fn) {
        String apngFile = getClass().getResource("/pngs/suite/" + fn).path
        String outFile = WORK_DIR + "/" + fn
        PngOptimizer optimizer = new PngOptimizer()
        optimizer.optimize(apngFile, outFile)
    }

    @Test
    void test_byte_op() {
        byte b = 127
        println b
        println String.format("b                 %8H", b)
        println b & 0xff
        println String.format("b & 0xff          %8H", b & 0xff)
        println "-------------------------------"

        b = -128
        println b
        println String.format("b                 %8H", b)
        println b & 0xff
        println String.format("b & 0xff          %8H", b & 0xff)
        println "-------------------------------"

        int i = 128
        println i
        println String.format("i                 %8H", i)
        println((byte) i)
        println String.format("(byte) i          %8H", (byte) i)
        println((byte) (i & 0xff))
        println String.format("(byte) (i & 0xff) %8H", (byte) (i & 0xff))
        println "-------------------------------"

        i = -129
        println i
        println String.format("i                 %8H", i)
        println((byte) i)
        println String.format("(byte) i          %8H", (byte) i)
        println((byte) (i & 0xff))
        println String.format("(byte) (i & 0xff) %8H", (byte) (i & 0xff))
        println "-------------------------------"


        byte a = -128
        b = 127
        println a
        println String.format("a                 %8H", a)
        println b
        println String.format("b                 %8H", b)
        println a-b
        println String.format("a-b               %8H", a-b)
        println((byte) (a-b))
        println String.format("(byte) a-b        %8H", (byte) (a-b))
        println((byte) ((a-b) & 0xff))
        println String.format("(byte)((a-b)&0xff)%8H", (byte) ((a-b) & 0xff))
        println "-------------------------------"
    }


    void toFile(byte[] dat, String fn) {
        FileOutputStream fos = new FileOutputStream(fn)
        fos.write(dat)
        fos.flush()
    }
}
