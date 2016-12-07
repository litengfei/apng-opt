package li.tengfei.apng.opt.builder;

import li.tengfei.apng.base.FormatNotSupportException;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Apng Rebuilder
 *
 * @author ltf
 * @since 16/12/6, 下午4:33
 */
public class ApngRebuilder {

    private PngData mApngData;

    private ArrayList<PngData> mFrameData;

    public boolean rebuild(String srcApngFile, String shrinkedImgsDir, String outFile) {
        try {
            mApngData = new PngReader(srcApngFile).getPngData();
            mFrameData = new PngsCollector().getPngs(shrinkedImgsDir);


            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (FormatNotSupportException e) {
            e.printStackTrace();
        }
        return false;
    }
}
