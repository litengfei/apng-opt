package li.tengfei.apng.opt.builder

import org.testng.annotations.Test

import static li.tengfei.apng.base.ApngConst.CODE_PLTE
import static li.tengfei.apng.base.ApngConst.CODE_tRNS

/**
 *
 * @author ltf
 * @since 16/12/7, 下午3:21
 */
class PngsCollectorTest {

    @Test
    void testGetPngs() {
        String shrinkedImgsDir = getClass().getResource("/pngs/diamondstar-apngasm_frames_opt").path
        PngsCollector collector = new PngsCollector();
        collector.getPngs(shrinkedImgsDir).each {
            it.chunks.each {
//                if (it.typeCode == CODE_PLTE) {
//                    println((it.data.size() - 12) / 3)
//                }
                if (it.typeCode == CODE_tRNS) {
                    println(it.data.size())
                }
            }
        }
    }
}
