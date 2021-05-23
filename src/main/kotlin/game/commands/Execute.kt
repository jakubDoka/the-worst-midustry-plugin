package game.commands

import db.Driver
import db.Ranks

class Execute(val driver: Driver): Command("execute", Ranks.Control.Absolute) {
    override fun run(args: Array<String>): Enum<*> {
        return try{
            val res = driver.exec(args[0])
            send("execute.success", res)
            Generic.Success
        } catch (e: Exception) {
            send("execute.failed", e.message ?: "error does not even have message")
            Result.Failed
        }
    }

    enum class Result {
        Denied, Failed
    }
}