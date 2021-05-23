package game.commands

import db.Quest
import db.Ranks

class RankInfo(private val ranks: Ranks, private val quests: Quest.Quests): Command("ranks") {
    override fun run(args: Array<String>): Enum<*> {
        return when (args[0]) {
            "all" -> {
                val sb = StringBuilder()
                val user = data

                fun postfix(rank: Ranks.Rank): String {
                    val post = if (kind == Kind.Discord) rank.name else rank.postfix
                    return if (user?.owns(rank.name) != true) post
                    else "[green]([]$post[green])[]"
                }

                for ((_, v) in ranks) {
                    sb.append(postfix(v)).append(" ")
                }

                alert("ranks.all.title", "placeholder", sb.toString())
                Result.All
            }

            else -> {
                val rank = ranks[args[0]]
                if (rank == null) {
                    send("ranks.notFound")
                    Generic.NotFound
                } else {
                    val sb = StringBuilder()
                    val user = data
                    for ((q, a) in rank.quest) {
                        sb.append("[gray]").append(q).append(":[] ")
                        if (user == null) sb.append(a)
                        else sb.append(quests[q]?.check(user, a) ?: bundle.translate("ranks.missingQuest"))
                        sb.append("\n")
                    }
                    alert(
                        "ranks.rank.title",
                        "ranks.rank.body",
                        rank.color,
                        rank.displayed,
                        rank.control,
                        rank.perms.joinTo(StringBuilder(), " ").toString(),
                        rank.value,
                        rank.kind,
                        rank.description[bundle.locale]
                            ?: rank.description["default"]
                            ?: bundle.translate("noDescription"),
                        rank.permanent,
                        sb.toString()
                    )
                    Generic.Success
                }
            }
        }
    }

    enum class Result {
        All
    }
}

