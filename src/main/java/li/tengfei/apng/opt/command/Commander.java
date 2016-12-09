package li.tengfei.apng.opt.command;

import com.beust.jcommander.JCommander;

/**
 * @author ltf
 * @since 16/12/9, 上午10:31
 */
public class Commander extends JCommander {
    public Commander(String[] args) {
        super();
        addCommand("extract", new CommandExtract(), "e");
        addCommand("rebuild", new CommandRebuild(), "b");
        parse(args);
    }

    public void run() {
        final boolean[] runed = {false};
        if (getCommands() != null && getCommands().get(getParsedCommand()) != null &&
                getCommands().get(getParsedCommand()).getObjects() != null) {
            getCommands().get(getParsedCommand()).getObjects().forEach(o -> {
                Command cmd = (Command) o;
                if (cmd != null) {
                    cmd.run();
                    runed[0] = true;
                }
            });
        }

        if (!runed[0]) usage();
    }
}
