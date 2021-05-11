package game

import db.Driver
import db.Ranks
import game.User.User
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry_plugin_utils.Logger
import mindustry_plugin_utils.Templates

class UserStore(private val driver: Driver, private val logger: Logger): HashMap<String, User>() {

    init {
        logger.on(EventType.PlayerConnect::class.java) {
            loadUser(it.player)
        }
    }

    fun loadUser(player: Player) {
        val existing = driver.searchUsers(player)
        val user = when (existing.size) {
            0 -> User(player, driver.newUser(player))
            1 -> User(player, existing[0])
            else -> {
                val sb = StringBuffer()
                for(e in existing) {
                    sb
                        .append("[yellow]")
                        .append(e.id)
                        .append(" [gray]")
                        .append(e.name)
                        .append(" [white]")
                        .append(e.rank)
                }
                val u = User(player, Driver.RawUser(driver.ranks))
                u.alert(u.translate("paralyzed.title"), "paralysed.body", sb.toString())
                u
            }
        }

        if(!user.idSpectator() && driver.banned(user.inner)) {
            driver.markGriefer(user.data.id)
            loadUser(user.inner)
            return
        }

        put(player.uuid(), user)
    }

    fun cleanUp() {
        filterValues {
            if (it.inner.con.hasDisconnected) {
                driver.saveUser(it.data)
                false
            } else {
                true
            }
        }
    }
}
