package game.commands

import game.Voting

class Vote(val voting: Voting): Command("vote") {
    override fun run(args: Array<String>): Enum<*> {
        val yes = when(args[0]) {
            "y" -> true
            "n" -> false
            else -> {
                send("wrongOption", "y n")
                return Generic.Mismatch
            }
        }

        val id = if(args.size > 1) {
            if(notNum(1, args)) {
                return Generic.NotAInteger
            }
            num(args[1]).toInt()
        } else 1

        if(voting.queue.isEmpty) {
            send("vote.nothing")
            return Result.Nothing
        }

        if(id < 1 || id > voting.queue.size) {
            send("vote.invalid", voting.queue.size)
            return Result.Invalid
        }


        if (voting.vote(id - 1, data!!, yes)) {
            send("vote.success", data!!.voteValue)
            return Generic.Success
        }

        send("vote.denied")
        return Generic.Denied
    }

    enum class Result {
        Nothing, Invalid, Denied
    }
}