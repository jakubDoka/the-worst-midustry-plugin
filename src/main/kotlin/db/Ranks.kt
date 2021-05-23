package db

import cfg.Reloadable
import com.beust.klaxon.Json
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import mindustry_plugin_utils.Fs
import mindustry_plugin_utils.Messenger
import mindustry_plugin_utils.Templates
import java.io.File

// Ranks holds all game ranks that are used
class Ranks(override val configPath: String = "config/ranks.json"): HashMap<String, Ranks.Rank>(), Reloadable {
    private val messenger = Messenger("Ranks")

    override fun reload() {
        try {
            val ranks = Klaxon().parse<Map<String, JsonObject>>(File(configPath))!!
            for ((k, v) in ranks) {
                put(k, Klaxon().parseFromJsonObject(v)!!)
            }
            for((k, v) in this) {
                v.name = k
            }
        } catch (e: Exception) {
            Fs.createDefault(configPath, this)
            messenger.log("failed to load config file: ${e.message}")
        }
    }

    // load default ranks
    init {
        put("paralyzed", Rank("#ff4d00", true, Control.Paralyzed, 0))
        put("griefer", Rank("#a10e0e", true, Control.None, 0))
        put(Driver.Users.defaultRank, Rank( "#fff", false, Control.Minimal))
        put("verified", Rank( "#248eb5", false, Control.Normal))
        put("candidate", Rank( "#830fa3", true, Control.Normal, 3))
        put("admin", Rank( "#ff6ed3", true, Control.High, 4))
        put("dev", Rank(
            "#faa #afa #aaf",
            true,
            Control.Absolute,
            perms = setOf(Perm.Skip)
        ))
        put("owner", Rank( "#d1c113", true, Control.Absolute))
        reload()
    }

    val default get() = get(Driver.Users.defaultRank)!!
    val griefer get() = get("griefer")!!
    val verified get() = get("verified")!!
    val admin get() = get("admin")!!
    val paralyzed get() = get("paralyzed")!!

    // enumerate lists all ranks of specific kind
    fun enumerate(kind: Kind): String {
        val sb = StringBuilder()
        forEach { _, v ->
            if(v.kind == kind)
                sb.append(v.postfix).append(" ")
        }
        return sb.toString()
    }

    // Rank holds information configured by user of plugin
    class Rank(
        val color: String = "red",
        val displayed: Boolean = true,
        val control: Control = Control.None,
        val voteValue: Int = 1,
        val perms: Set<Perm> = setOf(),
        val value: Int = 0,
        val kind: Kind = Kind.Normal,
        val quest: Map<String, Any> = mapOf(),
        val permanent: Boolean = true,
        val description: Map<String, String> = mapOf()
    ) {

        @Json(ignored = true)
        var name: String = "error"

        @Json(ignored = true)
        val postfix: String
            get() {
                val s = color.split(" ")
                return if (s.size == 1) "[$color]<$name>"
                else Templates.transition("<$name>", *Array(s.size){s[it]})
            }
        @Json(ignored = true)
        val display get() = if(displayed) postfix else ""
    }

    enum class Kind {
        Normal, Special, Premium
    }

    enum class Control {
        None, Paralyzed, Minimal, Normal, High, Absolute;

        object Counter {
            var id: Int = 0
            fun next(): Int {
                id++
                return id-1
            }
        }

        val value: Int = Counter.next()

        fun admin(): Boolean {
            return this.value >= High.value
        }

        fun spectator(): Boolean {
            return this.value <= Paralyzed.value
        }
    }

    enum class Perm {
        None, Skip, Scream, VoteKick
    }
}