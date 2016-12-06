package li.tengfei.apng.opt.optimizer;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Png Optimizer
 *
 * @author ltf
 * @since 16/12/5, 下午5:07
 */
public interface Optimizer {

    boolean optimize(InputStream in, OutputStream out);

}
