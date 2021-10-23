package the_worst_one.game.commands

import arc.Core
import arc.util.Time
import arc.util.Timer
import the_worst_one.cfg.Globals
import the_worst_one.cfg.Globals.time


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

                user.data.stats.lastDeath = Time.millis()

                Timer.schedule({ Core.app.post {
                    val unit = unitType.create(user.inner.team())
                    unit.set(user.inner.unit().x, user.inner.unit().y)
                    user.inner.unit().kill()
                    user.inner.unit(unit)
                    unit.add()
                    user.mount = unit
                    send("spawn.mount.success")
                } }, user.data.display.unitWarmUp.toFloat())

            }
        }

        return Generic.Success
    }

    enum class Result {
        Recharge, None
    }
}