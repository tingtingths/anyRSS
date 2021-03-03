package me.itdog.rssthis

import me.itdog.rssthis.web.RssService
import org.apache.commons.cli.*
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    RssThisApplication.start(args)
}

class RssThisApplication {

    companion object {
        fun start(args: Array<String>) {
            val parser: CommandLineParser = DefaultParser()
            val cmdLn: CommandLine
            val options = Options()
            options.addOption(
                Option.builder("p")
                    .longOpt("port")
                    .argName("port")
                    .type(Number::class.java)
                    .numberOfArgs(1)
                    .desc("Listen port")
                    .hasArg()
                    .required()
                    .build()
            )

            // parse arguments
            cmdLn = try {
                parser.parse(options, args)
            } catch (e: ParseException) {
                // print help message
                val helpFmt = HelpFormatter()
                helpFmt.printHelp("rssthis", options)
                exitProcess(1)
            }

            // start service
            val port = Integer.valueOf(cmdLn.getOptionValue("port", "80"))
            RssService(port)
        }
    }
}
