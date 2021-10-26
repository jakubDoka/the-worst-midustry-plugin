package the_worst_one.game.commands

import arc.Core
import the_worst_one.bundle.Bundle
import the_worst_one.cfg.Globals
import the_worst_one.cfg.Reloadable
import com.beust.klaxon.Klaxon
import the_worst_one.db.Driver
import the_worst_one.db.Quest
import the_worst_one.db.Ranks
import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import the_worst_one.game.Users
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mindustry.gen.Call
import mindustry_plugin_utils.Fs
import mindustry_plugin_utils.Logger
import mindustry_plugin_utils.Templates
import mindustry_plugin_utils.discord.Handler
import java.io.File
import java.util.*

class Discord(override var configPath: String = "config/discord.json", val logger: Logger, val driver: Driver, val users: Users, private val register: (Discord) -> Unit = {}): Reloadable {
    var handler: Handler? = null
    var config = Config()
    val verificationQueue = HashMap<Long, CodeData>()

    init {
        users.quests.reg(object: Quest("roles") {
            override fun check(user: Driver.RawUser, value: Any): String {
                if(user.discord == Driver.Users.noDiscord) return user.translate("quest.roles.none")
                if(handler == null) return user.translate("quest.roles.inactive")
                if(value !is String) return user.translate("quest.error")

                val roles = value.split(";")

                val gid = getServerID() ?: return user.translate("quest.roles.missingGuild")
                val u = handler!!.gateway.getMemberById(gid, Snowflake.of(user.discord)).block() ?: return user.translate("quest.roles.invalid")

                val roleSet = u.roles.collectList().block()?.map { it.name }?.toSet() ?: return user.translate("quest.roles.noRoles")
                val sb = StringBuilder()
                for(r in roles) {
                    if(!roleSet.contains(r)) sb.append(r).append(" ")
                }

                if(sb.isNotEmpty()) {
                    return user.translate("quest.roles.missing", sb.toString())
                }

                return complete
            }
        })
    }

    fun getServerID(): Snowflake? {
        return handler!!.gateway.guilds.blockFirst()?.id
    }

    override fun reload() {
        if(handler != null) {
            handler?.gateway?.logout()?.block()
        }

        try {
            config = Klaxon().parse<Config>(File(configPath))!!
        } catch (e: Exception) {
            e.printStackTrace()
            Globals.loadFailMessage("bot", e)
            Fs.createDefault(configPath, config)
        }

        if(!config.disabled) {
            println("bot:: connecting...")
            handler = Handler(config.token, config.prefix, commandChannel = config.commandChannel, loadChannels = config.channels)
            register.invoke(this)
            for((k, v) in config.permissions) {
                handler?.get(k)?.permissions?.addAll(v)
            }

            with("chat") {
                handler!!.gateway.on(MessageCreateEvent::class.java).subscribe { e ->
                    if(e.message.channelId.asString() != it.id.asString() || e.member.get().isBot) {
                        return@subscribe
                    }
                    val user = driver.users.load(e.member.get().id.asString())
                    val name = user?.idName() ?: e.member.get().nickname.orElse(e.member.get().username)
                    val content = user?.colorMessage(e.message.content) ?: e.message.content
                    val message = Globals.message(name, content)
                    Core.app.post {
                        Call.sendMessage(message)
                    }
                }
            }


                Globals.runLoggedGlobalScope {
                    handler!!.launch()
                }

            println("bot:: connected")
        }
    }

    fun logRankChange(by: Driver.RawUser?, user: Driver.RawUser, old: Ranks.Rank, new: Ranks.Rank, reason: String = "none") {
        val args = arrayOf(by?.idName() ?: "unknown", user.idName(), old.postfix, new.postfix, reason)
        val msg = Templates.cleanColors(Bundle.translate("setrank.success", *args))
        with("rankLog") {
            Globals.run { it.restChannel.createMessage(msg).block() }
        }

        users.send("setrank.success", *args)
        println(msg)
    }

    fun with(channel: String, run: (GuildChannel) -> Unit): Boolean {
        val ch = handler?.channels?.get(channel)
        if(ch != null) run(ch)
        return ch != null
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





