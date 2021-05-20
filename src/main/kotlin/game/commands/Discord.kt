package game.commands

import bundle.Bundle
import cfg.Reloadable
import com.beust.klaxon.Klaxon
import db.Driver
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.spec.EmbedCreateSpec
import game.Users
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mindustry_plugin_utils.Fs
import mindustry_plugin_utils.Logger
import mindustry_plugin_utils.Messenger
import mindustry_plugin_utils.Templates
import mindustry_plugin_utils.discord.Handler
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.lang.RuntimeException
import java.util.function.Consumer

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
            handler = Handler(config.token, config.prefix)
            register.invoke(this)
            for((k, v) in config.permissions) {
                handler?.get(k)?.permissions?.addAll(v)
            }
            runBlocking {
                GlobalScope.launch {
                    handler!!.launch()
                }
            }
            messenger.log("Connected.")
        }
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
    )
}





