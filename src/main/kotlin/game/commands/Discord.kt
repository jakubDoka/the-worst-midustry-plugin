package game.commands

import arc.Core
import bundle.Bundle
import cfg.Globals
import cfg.Reloadable
import com.beust.klaxon.Klaxon
import db.Driver
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mindustry.gen.Call
import mindustry_plugin_utils.Fs
import mindustry_plugin_utils.Logger
import mindustry_plugin_utils.Messenger
import mindustry_plugin_utils.discord.Handler
import java.io.File

class Discord(override val configPath: String = "config/discord.json", val logger: Logger, val driver: Driver, private val register: (Discord) -> Unit = {}): Reloadable {
    var handler: Handler? = null
    lateinit var messenger: Messenger
    lateinit var config: Config
    val verificationQueue = HashMap<Long, CodeData>()

    init {
        reload()
    }

    override fun reload() {
        if(handler != null) {
            handler?.gateway?.logout()?.block()
        }

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
            messenger.log("Connecting...")
            handler = Handler(config.token, config.prefix, commandChannel = config.commandChannel, loadChannels = config.channels)
            register.invoke(this)
            for((k, v) in config.permissions) {
                handler?.get(k)?.permissions?.addAll(v)
            }

            with("chat") {
                handler!!.gateway.on(MessageCreateEvent::class.java).subscribe { e ->
                    if(e.message.channelId.asString() != it.id.asString() && !e.member.get().isBot) {
                        return@subscribe
                    }

                    val author = driver.users.load(e.member.get().id.asString())?.idName()
                        ?: e.member.get().nickname.orElse(e.member.get().username)
                    val message = Globals.message(author, e.message.content)
                    Core.app.post {
                        Call.sendMessage(message)
                    }
                }
            }

            runBlocking {
                GlobalScope.launch {
                    handler!!.launch()
                }
            }
            messenger.log("Connected.")
        }
    }

    fun with(channel: String, run: (GuildChannel) -> Unit) {
        val ch = handler?.channels?.get(channel)
        if(ch != null) run(ch)
    }

    private fun initMessenger(verbose: Boolean) {
        messenger = Messenger("Discord", "enable verbose by adding '\"verbose\": true' to config", verbose)
    }

    fun reg(command: Command) {
        if(handler == null) {
            return
        }

        val args = Bundle.translateOr("${command.name}.discord.args", command.args)
        val desc =  Bundle.translateOr("${command.name}.discord.desc", Bundle.translate("${command.name}.desc"))
        command.kind = Command.Kind.Discord
        try {
            handler!!.reg(object: Handler.Cmd(command.name, args, desc){
                override fun run(message: Message, arguments: List<String>) {
                    logger.run {
                        command.message = message
                        command.author = driver.users.load(message.author.get().id.asString())
                        command.run(Array(arguments.size) {arguments[it]})
                        command.author = null
                    }
                }
            })
        } catch(e: Exception) {
            RuntimeException("failed to register ${command.name}", e).printStackTrace()
        }
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
        val commandChannel: String = "restrict commands to one channel",
        val channels: Map<String, String> = mapOf(
            "chat" to "id of live chat channel here",
            "commandLog" to "channel for logging commands here",
            "rankLog" to "rank change logging",
            "errorLog" to "error logging, can be very spammy",
            "maps" to "for publishing of maps"
        )
    )
}





