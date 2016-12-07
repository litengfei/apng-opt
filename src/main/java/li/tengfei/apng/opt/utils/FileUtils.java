package li.tengfei.apng.opt.utils;

import java.io.File;

/**
 * @author ltf
 * @since 16/10/9, 下午5:44
 */
public class FileUtils {
    /**
     * extract pure filename
     */
    public static String extractFilename(String pathFilename) {
        return pathFilename == null ? null : pathFilename.substring(pathFilename.lastIndexOf(File.separator) + 1);
    }

    /**
     * extract pure filename without ext name
     */
    public static String extractFilenameWithoutExt(String pathFilename) {
        pathFilename = extractFilename(pathFilename);
        return pathFilename == null ? null :
                pathFilename.lastIndexOf(".") < 0 ? pathFilename :
                        pathFilename.substring(0, pathFilename.lastIndexOf("."));
    }

    /**
     * extract file directory without last \ or /
     */
    public static String extractFileDir(String pathFilename) {
        return pathFilename == null ? null : pathFilename.substring(0, pathFilename.lastIndexOf(File.separator));
    }

    public static boolean mkDirs(String path) {
        return mkDirs(new File(path));
    }

    public static boolean mkDirs(File path) {
        return path.mkdirs();
    }
}
