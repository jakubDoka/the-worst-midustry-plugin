package game.commands

import db.Ranks
import mindustry_plugin_utils.Json
import mindustry_plugin_utils.Enums
import java.io.File

class Configure(val targets: Map<String, Reloadable>): Command("configure") {
    override fun run(args: Array<String>): Enum<*> {
        if (kind == Kind.Game && user!!.data.rank.control != Ranks.Control.Absolute) {
            send("configure.denied")
            return Result.Denied
        }

        val target = targets[args[0]]
        if (target == null) {
            val sb = StringBuilder()
            for((k, _) in targets) {
                sb.append(k).append(" ")
            }
            send("configure.unknown", sb.toString())
            return Result.Unknown
        }

        when(args[1]) {
            "view" -> {
                send("configure.view", target.view)
                return Result.View
            }
            "reload" -> {
                send("configure.reload")
                target.reload()
                return Result.Reload
            }
        }

        val r = when(args.size) {
            5 -> target.modify(args[1].capitalize(), args[2].capitalize(), args[3], args[4])
            3 -> target.modify(args[1].capitalize(), "Null", args[2], "")
            else -> {
                send("configure.count")
                return Result.Count
            }
        }

        val key = "configure.edit.${if(r != "") r else "success"}"
        when(r) {
            "type" ->  send(key, Enums.list(Json.Type::class.java))
            "method" -> send(key, Enums.list(Json.Method::class.java))
            else -> send(key)
        }

        return Result.Result
    }

    enum class Result {
        Unknown, Result, Reload, View, Count, Denied
    }

    interface Reloadable {
        fun reload()
        val configPath: String
        val view get() = try {
            File(configPath).readText()
        } catch (e: Exception) {
            "unableToOpen"
        }

        fun modify(method: String, type: String, path: String, value: String): String {
            val m = Enums.contains(Json.Method::class.java, method) ?: return "method"
            val t = Enums.contains(Json.Type::class.java, type) ?: return "type"
            return try {
                val j = Json(File(configPath).readText())
                val r = j.modify(m as Json.Method, t as Json.Type, path, value)
                File(configPath).writeText(j.toString())
                r
            } catch(e: Exception) {
                "osError"
            }
        }
    }
}