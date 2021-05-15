package game.commands

import arc.util.Time
import bundle.Bundle
import db.Driver
import kotlin.random.Random

class Link(val driver: Driver, val discord: Discord, val testing: Boolean = false): Command("link") {
    private val random = Random(Time.millis())
    override fun run(args: Array<String>): Enum<*> {
        if(notNum(args[0], 0)) {
            return Generic.NotAInteger
        }

        val id = num(args[0])

        if (!driver.users.exists(id)) {
            send("notFound")
            return Generic.NotFound
        }

        if (driver.users[id].password == Driver.Users.noPassword) {
            send("link.noPassword")
            return Result.NoPassword
        }

        send("link.success")
        val code = code()
        if(testing) {
            discord.verificationQueue[id] = Discord.CodeData(code, "")
            Bundle.send("link.code", code)
        } else {
            discord.verificationQueue[id] = Discord.CodeData(code, message!!.author.get().id.asString())
            message!!.sendPrivate("link.code", code)
        }
        return Generic.Success
    }

    private fun code(): String {
        return (random.nextInt(9999-1000) + 1000).toString()
    }

    enum class Result {
        NoPassword
    }
}