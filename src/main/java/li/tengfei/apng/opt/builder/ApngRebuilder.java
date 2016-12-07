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

    public boolean rebuild(String srcApngFile, String shrinkedImgsDir, String outFile) {
        try {
            RebuildApngReader apngReader = new RebuildApngReader(srcApngFile);
//            ArrayList<PngChunkData>


            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (FormatNotSupportException e) {
            e.printStackTrace();
        }
        return false;
    }
}
