package game.commands

import db.Driver
import db.Ranks

class Execute(val driver: Driver): Command("execute") {
    override fun run(args: Array<String>): Enum<*> {
        if (kind == Kind.Game && user!!.data.rank.control != Ranks.Control.Absolute) {
            user!!.send("execute.denied")
            return Result.Denied
        }

        return try{
            val res = driver.exec(args[0])
            send("execute.success", res)
            Result.Success
        } catch (e: Exception) {
            send("execute.failed", e.message ?: "error does not even have message")
            Result.Failed
        }
    }

    enum class Result {
        Denied, Failed, Success
    }
}