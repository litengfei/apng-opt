package li.tengfei.apng.opt

import org.testng.annotations.Test

/**
 *
 * @author ltf
 * @since 16/12/28, 下午2:24
 */
class RunnerTest {
    @Test
    void testMain() {
        String[] args = ["e",
                         "-i", "/Users/f/downloads/wx/羊驼/75%_.png.ang"]

//        String[] args = ["b",
//                         "-i", "/Users/f/downloads/wx/羊驼/75%_.png",
//                         "-o", "/Users/f/downloads/wx/羊驼/75%_.png.ang",
//                         "-pngs", "/Users/f/downloads/wx/羊驼/75%_.png.frames"]


        new Runner().main(args)
    }
}
