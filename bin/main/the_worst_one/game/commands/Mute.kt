package the_worst_one.game.commands

import the_worst_one.db.Driver
import the_worst_one.game.Users

class Mute(val driver: Driver, val users: Users): Command("mute") {
    override fun run(args: Array<String>): Enum<*> {
        val user = user!!

        if(notNum(0, args[0])) return Generic.NotAInteger

        val id = num(args[0])

        if(!driver.users.exists(id)) {
            send("notFound")
            return Generic.NotFound
        }

        if(args.size > 1) {
            if(user.data.rank.control.admin()) {
                val value = driver.users.get(id, Driver.Users.mutedForAll) ?: false
                driver.users.set(id, Driver.Users.mutedForAll, !value)

                if(value) send("mute.globallyAmplify")
                else send("mute.globallyMute")

                for(v in users.values) {
                    if(v.data.id == id) {
                        v.data.mutedForAll = !value
                        users.reload(v)
                        break
                    }
                }
            } else {
                send("mute.denied")
                return Generic.Denied
            }
        } else {
            if(user.data.muted.contains(id)) {
                user.data.muted.remove(id)
                send("mute.amplify")
            } else {
                if(id == user.data.id) send("mute.self")
                user.data.muted.add(id)
                send("mute.mute")
            }
        }

        return Generic.Success
    }
}