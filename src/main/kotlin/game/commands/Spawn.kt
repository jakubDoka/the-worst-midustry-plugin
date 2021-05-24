package game.commands

import arc.util.Time
import cfg.Globals
import mindustry_plugin_utils.Templates.time

class Spawn: Command("spawn") {
    override fun run(args: Array<String>): Enum<*> {
        val user = user!!
        when(args[0]) {
            "mount" -> {
                val left = Time.millis() - user.data.stats.lastDeath
                if(left < user.data.rank.unitRecharge) {
                    send("spawn.mount.recharge", left.time())
                    return Result.Recharge
                }

                val unitType = Globals.unit(user.data.display.unit) ?: Globals.unit(user.data.rank.unit)
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
                send("spawn.mount.success")
            }
        }

        return Generic.Success
    }

    enum class Result {
        Recharge, None
    }
}