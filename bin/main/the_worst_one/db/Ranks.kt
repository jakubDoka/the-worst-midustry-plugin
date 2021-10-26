package the_worst_one.db

import the_worst_one.cfg.Globals
import the_worst_one.cfg.Reloadable
import com.beust.klaxon.Json
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import mindustry_plugin_utils.Fs
import mindustry_plugin_utils.Templates
import java.io.File
import java.util.*

// Ranks holds all the_worst_one.game ranks that are used
class Ranks(override var configPath: String = "config/ranks.json"): HashMap<String, Ranks.Rank>(), Reloadable {
    override fun reload() {
        try {
            val ranks = Klaxon().parse<Map<String, JsonObject>>(File(configPath))!!
            for ((k, v) in ranks) {
                put(k, Klaxon().parseFromJsonObject(v)!!)
            }
        } catch (e: Exception) {
            Fs.createDefault(configPath, this)
            Globals.loadFailMessage("ranks", e)
        }

        for((k, v) in this) {
            v.name = k
        }
    }

    // load default ranks
    init {
        put("paralyzed", Rank(
            "#ff4d00",
            true,
            Control.Paralyzed,
            0,
            description = mapOf(
                "default" to "If server cannot assign any account to you, you will obtain this rank."
            )
        ))
        put("griefer", Rank(
            "#a10e0e",
            true,
            Control.None,
            0,
            description = mapOf(
                "default" to "For thous who are too stupid to play the_worst_one.game normally."
            )
        ))
        put(Driver.Users.defaultRank, Rank(
            "#ffffff",
            false,
            Control.Minimal,
            description = mapOf(
                "default" to "Casual rank that you will start with. Its bets to get rid of it verify your self."
            )
        ))
        put("verified", Rank(
            "#248eb5",
            false,
            Control.Normal,
            description = mapOf(
                "default" to "For verified members of community. Newcomers cannot interact with your blocks."
            )
        ))
        put("candidate", Rank(
            "#830fa3",
            true,
            Control.Normal,
            3,
            description = mapOf(
                "default" to "Ones closer to god. Candidate's vote has three times as big value in " +
                        "comparison to other ranks."
            )
        ))
        put("admin", Rank(
            "#ff6ed3",
            true,
            Control.High,
            4,
            description = mapOf(
                "default" to "Usually people who have lot of time to waste it as staff with no payment."
            )
        ))
        put("dev", Rank(
            "#faa #afa #aaf",
            true,
            Control.Absolute,
            perms = setOf(Perm.Skip),
            description = mapOf(
                "default" to "Only thous who supported server with least 3000 lines of code can own this" +
                        " rank. Of course code has to be at least as useful as code there already is."
            ),
            pets = listOf("somePet")
        ))
        put("owner", Rank(
            "#d1c113",
            true,
            Control.Absolute,
            description = mapOf(
                "default" to "Person who pays all the bills for the server. The most p2w rank there is."
            )
        ))
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
        var control: Control = Control.None,
        val voteValue: Int = 1,
        val perms: Set<Perm> = setOf(),
        val value: Int = 0,
        val kind: Kind = Kind.Normal,
        val quest: Map<String, Any> = mapOf(),
        val permanent: Boolean = true,
        val description: Map<String, String> = mapOf(),
        val unit: String = "",
        val unitRecharge: Long = 0,
        val unitWarmUp: Long = 0,
        val pets: List<String> = listOf(),
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
        None, Skip, Scream, VoteKick, Maps, Store, Load, BuildCore, Boost
    }
}