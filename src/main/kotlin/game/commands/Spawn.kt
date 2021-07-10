package game.commands

import arc.util.Time
import cfg.Globals
import cfg.Globals.time


class Spawn: Command("spawn") {
    override fun run(args: Array<String>): Enum<*> {
        val user = user!!
        when(args[0]) {
            "mount" -> {

                var rank = user.data.display
                val unitType = Globals.unit(user.data.display.unit) ?: run {
                    rank = user.data.rank
                    Globals.unit(user.data.rank.unit)
                }

                if(unitType == null) {
                    send("spawn.mount.none")
                    return Result.None
                }

                val left = user.data.stats.lastDeath - Time.millis() + rank.unitRecharge
                if(left > 0) {
                    send("spawn.mount.recharge", left.time())
                    return Result.Recharge
                }

                val unit = unitType.create(user.inner.team())
                unit.set(user.inner.unit().x, user.inner.unit().y)
                user.inner.unit().kill()
                user.inner.unit(unit)
                unit.add()
                user.mount = unit
                send("spawn.mount.success")
            }
        }

        return Generic.Success
    }

    enum class Result {
        Recharge, None
    }
}