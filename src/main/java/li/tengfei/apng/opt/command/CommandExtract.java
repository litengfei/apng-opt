package li.tengfei.apng.opt.command;


import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import li.tengfei.apng.opt.builder.ApngExtractor;

/**
 * @author ltf
 * @since 16/12/9, 上午10:32
 */
@Parameters(commandDescription = "Extract apng file to pngs frame")
public class CommandExtract implements Command {

    @Parameter(names = {"-i"},
            description = "input file or directory",
            required = true)
    private String inApng;

    @Parameter(names = {"-k"},
            description = "api key for tinypng.com",
            required = false)
    private String tinyKey = null;


    @Override
    public void run() {
        new ApngExtractor().extract(inApng, tinyKey);
    }
}
