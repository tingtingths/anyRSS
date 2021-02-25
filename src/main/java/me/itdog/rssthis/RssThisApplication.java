package me.itdog.rssthis;


import me.itdog.rssthis.web.RssService;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class RssThisApplication {

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmdLn;

        Options options = new Options();
        options.addOption(Option.builder("p")
                .longOpt("port")
                .argName("port")
                .type(Number.class)
                .numberOfArgs(1)
                .desc("Listen port")
                .hasArg()
                .required()
                .build());

        try {
            cmdLn = parser.parse(options, args);
        } catch (ParseException e) {
            HelpFormatter helpFmt = new HelpFormatter();
            helpFmt.printHelp("rssthis", options);
            System.exit(1);
            return;
        }

        Integer port = Integer.valueOf(cmdLn.getOptionValue("port", "80"));
        new RssService(port);
    }
}
