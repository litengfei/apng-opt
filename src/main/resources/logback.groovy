import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.ConsoleAppender

import static ch.qos.logback.classic.Level.DEBUG
import static ch.qos.logback.classic.Level.WARN

appender("STDOUT", ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = "%-5level - %msg%n"
    }
}

logger('li.tengfei.apng.opt.optimizer.KMeansReducer', WARN)
root(DEBUG, ["STDOUT"])