package li.tengfei.apng.opt.builder;

import java.io.File;
import java.util.ArrayList;

/**
 * Png Files Collector
 *
 * @author ltf
 * @since 16/12/7, 下午1:01
 */
public class PngsCollector {

    public ArrayList<PngData> getPngs(String imgsDir) {
        File dir = new File(imgsDir);
        ArrayList<PngData> pngs = new ArrayList<>();
        if (dir.exists() && dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                // only process png files
                if (!f.getName().toLowerCase().endsWith("png"))
                    continue;

                try {
                    pngs.add(new PngReader(f).getPngData());
                } catch (Exception e) {
                    throw new RuntimeException("FileError: " + f.getName() + ", " + e.getMessage());
                }
            }
        }

        return pngs;
    }

}
