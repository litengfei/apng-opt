package li.tengfei.apng.opt.builder

import org.testng.annotations.Test

/**
 *
 * @author ltf
 * @since 16/12/7, 下午3:21
 */
class PngsCollectorTest {

    @Test
    void testGetPngs() {
        String shrinkedImgsDir = getClass().getResource("/pngs/diamondstar-apngasm").path
        PngsCollector collector = new PngsCollector();
        collector.getPngs(shrinkedImgsDir).each {
            println(it.chunks.size())
        }
    }
}
