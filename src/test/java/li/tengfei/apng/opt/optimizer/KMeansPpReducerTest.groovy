package li.tengfei.apng.opt.optimizer

import org.testng.annotations.Test

import javax.imageio.ImageIO
import java.awt.*
import java.awt.image.BufferedImage

import static li.tengfei.apng.opt.shrinker.TestConst.getWORK_DIR

/**
 *
 * @author ltf
 * @since 16/12/16, 上午10:48
 */
class KMeansPpReducerTest {
    @Test
    void testReduce() {
        String inFile = getClass().getResource("/jpgs/hokkaido.jpg").path
        String outFile = WORK_DIR + "/hokkaido-color-reduce.jpg"

        BufferedImage img = ImageIO.read(new File(inFile))
        Color[] pixels = new Color[img.width * img.height]
        for (int h = 0; h < img.height; h++) {
            for (int w = 0; w < img.width; w++) {
                pixels[h * img.width + w] = new Color(img.getRGB(w, h), false)
            }
        }

        Map<Color, Color> mapping = new KMeansPpReducer().reduce(pixels, 2048)
        for (int h = 0; h < img.height; h++) {
            for (int w = 0; w < img.width; w++) {
                img.setRGB(w, h, mapping.get(pixels[h * img.width + w]).getRGB())
            }
        }

        ImageIO.write(img, "jpg", new File(outFile))
    }
}
