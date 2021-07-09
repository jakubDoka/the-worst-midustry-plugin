package game.commands

import arc.Core
import bundle.Bundle
import cfg.Globals
import cfg.Globals.ensure
import db.Driver
import db.Ranks
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Color
import game.u.User
import mindustry_plugin_utils.Templates
import java.io.File
import java.lang.Long.parseLong
import java.util.function.Consumer

// Command is a base class for all commands and implements some common utility
abstract class Command(val name: String, val control: Ranks.Control = Ranks.Control.Minimal) {
    val args = Bundle().get("$name.args")
    var kind: Kind = Kind.Game

    var user: User? = null
    var message: Message? = null
    var author: Driver.RawUser? = null

    val dm = DiscordMessenger(this)

    // main executor of command
    abstract fun run(args: Array<String>): Enum<*>

    // short hand for posting to main thread, this is used in bot commands
    fun post(r: () -> Unit) {
        if(kind != Kind.Discord) r()
        else Core.app.post(r)
    }

    fun ensure(a: Array<String>, count: Int): Boolean {
        return if(a.size < count) {
            send("notEnoughArgs", a.size, count)
            true
        } else {
            false
        }
    }

    fun notNum(argument: Int, args: Array<String>, async: Boolean = false): Boolean {
        return notNum(argument, args[argument], async)
    }

    // checks if string is valid number and notifies user if not
    fun notNum(argument: Int, arg: String, async: Boolean = false): Boolean {
        return try {
            parseLong(arg)
            false
        } catch (e: Exception) {
            if(async) post {
                send("notANumber", argument + 1)
            } else {
                send("notANumber", argument + 1)
            }
            true
        }
    }

    // parses a number
    fun num(s: String): Long {
        return parseLong(s)
    }

    // used for texting purposes
    fun assert(result: Enum<*>, vararg args: String) {
        val res = run(Array(args.size) { args[it] })
        if (res != result) {
            println("got: $res expected: $result")
            assert(false)
        }
    }

    fun sendPlain(text: String) {
        when(kind) {
            Kind.Discord -> dm.sendPlain(text)
            Kind.Game -> user!!.sendPlain(text)
            Kind.Cmd -> println(text)
        }
    }

    // generic send method base dof current command kind
    fun send(key: String, vararg args: Any) {
        when(kind) {
            Kind.Discord -> dm.send(key, *args)
            Kind.Game -> user!!.send(key, *args)
            Kind.Cmd -> Bundle.send(key, *args)
        }
    }

    fun alert(titleKey: String, bodyKey: String, vararg args: Any) {
        when(kind) {
            Kind.Game -> user!!.alert(titleKey, bodyKey, *args)
            else -> dm.alert(titleKey, bodyKey, *args)
        }
    }

    fun alertPlain(text: String) {
        when(kind) {
            Kind.Game -> user!!.alert(text)
            else -> dm.alertPlain(text)
        }
    }

    val bundle get() = when(kind) {
        Kind.Game -> user!!.data.bundle
        else -> author?.bundle ?: Bundle.defaultBundle
    }

    val data get() = user?.data ?: author

    fun loadDiscordAttachment(to: String): File {
        val map = message!!.attachments.first().data
        val file = File(Globals.botRoot + "$to/" + map.filename())
        file.ensure()

        file.writeBytes(Globals.downloadAttachment(map.url()){ _, mono -> mono.asByteArray()})

        return file
    }

    class DiscordMessenger(val command: Command) {
        fun send(key: String, vararg args: Any) {
            sendPlain(translate(key, *args))
        }

        fun sendPlain(text: String) {
            send(command.message!!.channel.block(), text)
        }

        fun sendPrivate(key: String, vararg args: Any): Boolean {
            return sendPrivatePlain(translate(key, *args))
        }

        fun sendPrivatePlain(text: String): Boolean {
            return send(command.message!!.author.get().privateChannel.block(), text)
        }

        fun send(channel: MessageChannel?, text: String): Boolean {
            if(command.message == null) Templates.cleanColors(text)
            else channel?.createMessage(Templates.cleanColors(text))?.block() ?: return false
            return true
        }

        fun alert(titleKey: String, bodyKey: String, vararg arguments: Any) {
            send {
                it.setTitle(translate(titleKey))
                it.setDescription(translate(bodyKey, *arguments))
                it.setColor(Color.CYAN)
            }
        }

        fun alertPlain(text: String) {
            send {
                it.setDescription(text)
            }
        }

        fun translate(key: String, vararg args: Any): String {
            return clean(command.author?.translate(key, *args) ?: Bundle.translate(key, *args))
        }

        fun send(embed: Consumer<EmbedCreateSpec>) {
            if(command.message == null) {
                val e = EmbedCreateSpec()
                embed.accept(e)
                println(e.asRequest().title().get())
                println(e.asRequest().description().get())
            } else command.message!!.channel.block()?.createEmbed(embed)?.block()
        }

        fun clean(message: String): String {
            return Templates.colorR.matcher(message).replaceAll("**")
        }
    }

    enum class Kind {
        Cmd, Game, Discord
    }

    enum class Generic {
        Success, NotAInteger, Mismatch, NotEnough, NotFound, NotSupported, Denied, Vote, LoadFailed, NoFreeShips
    }
}