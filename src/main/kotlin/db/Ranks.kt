package db

import com.beust.klaxon.Klaxon
import game.commands.Discord
import mindustry_plugin_utils.Messenger
import util.Fs
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

// Ranks holds all game ranks that are used
class Ranks(path: String = ""): HashMap<String, Ranks.Rank>() {
   companion object {
        val paralyzed = Rank("paralyzed", "#ff4d00", true, Control.None)
        const val griefer = "griefer"
    }

    private val messenger = Messenger("Ranks")

    // loads config file
    init {
        try {
            val ranks = Klaxon().parse<HashMap<String, Rank>>(File(path))!!
            for ((k, v) in ranks) put(k, v)
        } catch (e: FileNotFoundException) {
            Fs.createDefault(path, this)
        } catch (e: IOException) {
            Fs.createDefault(path, this)
        } catch (e: Exception) {
            messenger.log("failed to load config file: ${e.message}")
        }
    }

    // load default ranks
    init {
        put("griefer", Rank("griefer", "##a10e0e", true, Control.None))
        put(Driver.Users.defaultRank, Rank(Driver.Users.defaultRank, "#b58e24", false, Control.Minimal))
        put("verified", Rank("verified", "#248eb5", false, Control.Normal))
        put("candidate", Rank("candidate", "#830fa3", false, Control.Normal))
        put("admin", Rank("admin", "#ff6ed3", true, Control.High, true))
        put("dev", Rank("dev", "#19a30f", true, Control.Absolute, true))
        put("owner", Rank("owner", "#d1c113", true, Control.Absolute, true))
    }

    val default get() = get(Driver.Users.defaultRank)!!
    val griefer get() = get("griefer")!!
    val admin get() = get("admin")!!

    // enumerate lists all ranks of specific kind
    fun enumerate(kind: Kind): String {
        val sb = StringBuilder()
        forEach { _, v ->
            if(v.kind == kind)
                sb.append(v.postfix)
        }
        return sb.toString()
    }

    // Rank holds information configured by user of plugin
    class Rank(
        val name: String = "error",
        val color: String = "red",
        val displayed: Boolean = true,
        val control: Control = Control.None,
        val admin: Boolean = false,
        val perms: Set<Perm> = setOf(),
        val value: Int = 0,
        val kind: Kind = Kind.Normal,
    ) {
        val postfix get() = "[$color]<$name>[]"
        val display get() = if(displayed) postfix else ""
        val permanent get() = true
    }

    enum class Kind {
        Normal, Special, Premium
    }

    enum class Control {
        None, Minimal, Normal, High, Absolute;

        fun mutable(): Boolean {
            return this != High && this != Absolute
        }
    }

    enum class Perm
}