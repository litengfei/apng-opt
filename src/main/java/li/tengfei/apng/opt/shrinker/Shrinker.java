package li.tengfei.apng.opt.shrinker;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Png Optimizer
 *
 * @author ltf
 * @since 16/12/5, 下午5:07
 */
public interface Shrinker {

    boolean optimize(InputStream in, OutputStream out);

}
