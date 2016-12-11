package li.tengfei.apng.opt.optimizer;

import li.tengfei.apng.opt.builder.AngData;

/**
 * Ang format Optimizer
 *
 * @author ltf
 * @since 16/12/11, 下午2:19
 */
public interface AngOptimizer {
    AngData optimize(AngData ang);
}
