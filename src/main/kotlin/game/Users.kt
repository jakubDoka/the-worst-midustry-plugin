package game

import arc.Core
import arc.util.Timer
import db.Driver
import db.Ranks
import game.u.User
import mindustry.game.EventType
import mindustry.gen.Player
import mindustry.net.Net
import mindustry.net.NetConnection
import mindustry_plugin_utils.Logger

// Users keeps needed data about users in ram memory
class Users(private val driver: Driver, private val logger: Logger, val ranks: Ranks, testing: Boolean = false): HashMap<String, User>() {


    init {
        logger.on(EventType.PlayerConnect::class.java) {
            load(it.player)
        }

        // clean users every now and then
        if(!testing) Timer.schedule({
            Core.app.post { cleanUp() }
        }, 10f)
    }

    fun reload(target: User) {
        load(target.inner)
    }

    fun find(id: Long): User? {
        var user: User? = null
        forEach { _, u ->
            if(u.data.id == id) {
                user = u
                return@forEach
            }
        }
        return user
    }

    fun test(ip: String = "127.0.0.1", name: String = "name"): User {
        val p = Player.create()
        p.name = name
        p.con = object: NetConnection(ip) {
            override fun send(p0: Any?, p1: Net.SendMode?) {}
            override fun close() {}
        }

        val ru = driver.users.new(p)
        val u = User(p, ru, true)
        put(u.data.uuid, u)

        return u
    }

    fun load(player: Player) {
        val existing = driver.users.search(player)
        val user = when (existing.size) {
            0 -> User(player, driver.users.new(player))
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
                        .append(e.rank.postfix)
                        .append("\n")
                }
                val u = User(player, Driver.RawUser(driver.ranks))
                u.alert(u.translate("paralyzed.title"), "paralyzed.body", sb.toString())
                u
            }
        }

        if(!user.idSpectator() && driver.banned(user.inner)) {
            driver.users[user.data.id].rank = ranks.griefer
            reload(user)
            return
        }

        put(player.uuid(), user)
    }

    private fun cleanUp() {
        filterValues {
            !it.inner.con.hasDisconnected
        }
    }


}
