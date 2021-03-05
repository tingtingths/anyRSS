package me.itdog.rssthis

import me.itdog.rssthis.web.RssService
import org.apache.commons.cli.*
import java.net.InetSocketAddress
import java.net.Proxy
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

            // port
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

            // proxy type
            options.addOption(
                Option.builder()
                    .longOpt("proxy-type")
                    .argName("proxy type")
                    .type(String::class.java)
                    .numberOfArgs(1)
                    .desc("Proxy type [http, socks]")
                    .hasArg()
                    .build()
            )

            // proxy host
            options.addOption(
                Option.builder()
                    .longOpt("proxy-host")
                    .argName("proxy host")
                    .type(String::class.java)
                    .numberOfArgs(1)
                    .desc("Proxy host")
                    .hasArg()
                    .build()
            )

            // proxy port
            options.addOption(
                Option.builder()
                    .longOpt("proxy-port")
                    .argName("proxy port")
                    .type(Integer::class.java)
                    .desc("Proxy port")
                    .hasArg()
                    .build()
            )

            // parse arguments
            cmdLn = try {
                parser.parse(options, args)
            } catch (e: ParseException) {
                printHelp(options)
                exitProcess(1)
            }
            argumentsInvalidReason(cmdLn)?.run {
                println(this)
                printHelp(options)
                exitProcess(1)
            }

            // prepare arguments
            val port = Integer.valueOf(cmdLn.getOptionValue("port", "80"))
            val proxy: Proxy? = cmdLn.getOptionValue("proxy-type")?.let { type ->
                val proxyHost = cmdLn.getOptionValue("proxy-host")
                val proxyPort = Integer.valueOf(cmdLn.getOptionValue("proxy-port"))
                Proxy(Proxy.Type.valueOf(type.toUpperCase()), InetSocketAddress(proxyHost, proxyPort))
            }

            // start service
            RssService(port, proxy)
        }

        private fun argumentsInvalidReason(cmdLine: CommandLine): String? {
            if (cmdLine.hasOption("proxy-type")
                && !listOf("http", "socks").contains(cmdLine.getOptionValue("proxy-type"))
            ) {
                return "proxy type must be one of [http, socks]"
            }
            if (cmdLine.hasOption("proxy-type")
                && (!cmdLine.hasOption("proxy-host") || !cmdLine.hasOption("proxy-port"))
            ) {
                return "proxy host/port must be provided"
            }
            return null
        }

        private fun printHelp(options: Options) {
            val helpFmt = HelpFormatter()
            helpFmt.printHelp("rssthis", options)
        }
    }
}
