package game.commands

import arc.util.Time
import cfg.Globals
import mindustry.content.UnitTypes

class Spawn: Command("spawn") {
    override fun run(args: Array<String>): Enum<*> {
        val user = user!!
        when(args[0]) {
            "mount" -> {
                if(Time.millis() - user.data.stats.lastDeath < user.data.rank.unitRecharge) {
                    send("spawn.mount.recharge")
                    return Result.Recharge
                }

                val unitType = Globals.unit(user.data.rank.unit)
                if(unitType == null) {
                    send("spawn.mount.none")
                    return Result.None
                }

                val unit = unitType.create(user.inner.team())
                unit.set(user.inner.unit().x, user.inner.unit().y)
                user.inner.unit().kill()
                user.inner.unit(unit)
                unit.add()
                user.mount = unit
            }
        }

        return Generic.Success
    }

    enum class Result {
        Already, Recharge, None
    }
}