package game.commands

import bundle.Bundle
import com.beust.klaxon.Klaxon
import discord4j.core.`object`.entity.Message
import mindustry_plugin_utils.Messenger
import mindustry_plugin_utils.discord.Handler
import util.Fs
import java.io.File
import java.io.FileNotFoundException

class Discord(configPath: String) {
    var handler: Handler? = null
    lateinit var messenger: Messenger
    var config: Config

    init {
        try {
            config = Klaxon().parse<Config>(File(configPath))!!
            initMessenger(config.verbose)
        } catch (e: FileNotFoundException) {
            config = Config()
            Fs.createDefault(configPath, config)
        } catch (e: Exception) {
            initMessenger(false)
            messenger.log("failed to load config")
            messenger.verbose {
                e.printStackTrace()
            }
            config = Config()
        }

        if(!config.disabled) {
            handler = Handler(config.token, config.prefix)
            for((k, v) in config.permissions) {
                handler?.get(k)?.permissions?.addAll(v)
            }
        }
    }

    private fun initMessenger(verbose: Boolean) {
        messenger = Messenger("DatabaseDriver", "enable verbose by adding '\"verbose\": true' to config", verbose)
    }

    fun reg(command: Command) {
        if(handler == null) {
            return
        }

        command.kind = Command.Kind.Discord
        handler!!.reg(object: Handler.Cmd(command.name, command.args, Bundle.translate("${command.name}.desc")){
            override fun run(message: Message, arguments: List<String>) {
                command.message = message
                command.run(Array(arguments.size) {arguments[it]})
            }

        })
    }

    class Config(
        val disabled: Boolean = true,
        val verbose: Boolean = false,
        val token: String = "",
        val prefix: String = "!",
        val permissions: HashMap<String, Array<String>> = HashMap(),
    )
}

fun Message.send(key: String, vararg args: Any) {
    plainReply(Bundle.translate(key, *args))
}

fun Message.plainReply(text: String) {
    channel.block()?.createMessage(text)?.block()
}