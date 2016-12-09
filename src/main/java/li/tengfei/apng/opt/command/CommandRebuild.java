package li.tengfei.apng.opt.command;


import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import li.tengfei.apng.opt.builder.ApngRebuilder;

/**
 * @author ltf
 * @since 16/12/9, 上午10:32
 */
@Parameters(commandDescription = "Rebuild apng file to .ang format")
public class CommandRebuild implements Command {

    @Parameter(names = {"-i"},
            description = "input apng file",
            required = true)
    private String inApng;

    @Parameter(names = {"-pngs"},
            description = "input frames directory",
            required = true)
    private String inFramePngsDir;

    @Parameter(names = {"-o"},
            description = "output .ang file",
            required = true)
    private String outAng;


    @Override
    public void run() {
        new ApngRebuilder().rebuild(inApng, inFramePngsDir, outAng);
    }
}
