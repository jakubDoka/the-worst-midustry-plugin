package game.commands

import bundle.Bundle
import com.beust.klaxon.Klaxon
import discord4j.core.`object`.entity.Message
import discord4j.core.spec.EmbedCreateSpec
import mindustry_plugin_utils.Fs
import mindustry_plugin_utils.Messenger
import mindustry_plugin_utils.Templates
import mindustry_plugin_utils.discord.Handler
import java.io.File
import java.util.function.Consumer

class Discord(override val configPath: String = "config/discord.json"): Configure.Reloadable {
    var handler: Handler? = null
    lateinit var messenger: Messenger
    lateinit var config: Config
    val verificationQueue = HashMap<Long, CodeData>()

    init {
        reload()
    }

    override fun reload() {
        try {
            config = Klaxon().parse<Config>(File(configPath))!!
            initMessenger(config.verbose)
        } catch (e: Exception) {
            initMessenger(false)
            messenger.log("failed to load config")
            messenger.verbose {
                e.printStackTrace()
            }
            config = Config()
            Fs.createDefault(configPath, config)
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

        val args = Bundle.translateOr("${command.name}.discord.args", command.args)
        val desc =  Bundle.translateOr("${command.name}.discord.desc", Bundle.translate("${command.name}.desc"))
        println(args)
        println(desc)
        command.kind = Command.Kind.Discord
        handler!!.reg(object: Handler.Cmd(command.name, args, desc){
            override fun run(message: Message, arguments: List<String>) {
                command.message = message
                command.run(Array(arguments.size) {arguments[it]})
            }

        })
    }

    class CodeData(val code: String = "", val id: String = "")

    class Config(
        val disabled: Boolean = true,
        val verbose: Boolean = false,
        val token: String = "",
        val prefix: String = "!",
        val permissions: Map<String, List<String>> = mapOf(
            "execute" to listOf("roleName", "otherRoleName")
        ),
    )
}

fun Message.send(key: String, vararg args: Any) {
    plainReply(Bundle.translate(key, *args))
}

fun Message.plainReply(text: String) {
    channel.block()?.createMessage(Templates.cleanColors(text))?.block()
}

fun Message.sendPrivate(key: String, vararg args: Any) {
    sendPrivatePlain(Bundle.translate(key, *args))
}

fun Message.sendPrivatePlain(text: String) {
    author.get().privateChannel.block()?.createMessage(text)?.block()
}

fun Message.send(embed: Consumer<EmbedCreateSpec>) {
    channel.block()?.createEmbed(embed)?.block()
}