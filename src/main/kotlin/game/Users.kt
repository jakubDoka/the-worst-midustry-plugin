package game

import arc.Core
import arc.util.Timer
import cfg.Config
import db.Driver
import db.Ranks
import game.u.User
import mindustry.game.EventType
import mindustry.gen.Player
import mindustry.net.Net
import mindustry.net.NetConnection
import mindustry_plugin_utils.Logger

// Users keeps needed data about users in ram memory
class Users(private val driver: Driver, private val logger: Logger, val ranks: Ranks, val config: Config, testing: Boolean = false): HashMap<String, User>() {


    init {
        logger.on(EventType.PlayerConnect::class.java) {
            load(it.player)
        }

        logger.on(EventType.PlayerChatEvent::class.java) {
            val user = get(it.player.uuid())!!
            if(it.message.startsWith("/")) {
                user.data.stats.commands++
            } else {
                user.data.stats.messages++
            }
        }

        logger.on(EventType.GameOverEvent::class.java) {
            forEach { _, u ->
                if(u.inner.team() == it.winner) {
                    u.data.stats.won++
                }
                u.data.stats.played++
            }
        }

        logger.on(EventType.UnitDestroyEvent::class.java) {
            var uuid: String? = null
            if(it.unit.isPlayer) {
                val user = get(it.unit.player.uuid())!!
                user.data.stats.deaths++
                uuid = user.inner.uuid()
            }

            forEach { _, u ->
                if(u.inner.team() != it.unit.team && u.inner.uuid() != uuid) {
                    u.data.stats.killed++
                }
            }
        }

        logger.on(EventType.BlockBuildEndEvent::class.java) {
            if(!it.unit.isPlayer || it.tile.block().buildCost < config.data.minBuildCost) {
                return@on
            }

            val user = get(it.unit.player.uuid())!!
            if(it.breaking) {
                user.data.stats.destroyed++
            } else {
                user.data.stats.build++
            }
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
            if(it.inner.con.hasDisconnected) {
                driver.users.save(it.data)
                false
            } else {
                true
            }
        }
    }


}
