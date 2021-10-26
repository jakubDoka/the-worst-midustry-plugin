package the_worst_one.game

import arc.math.Mathf
import arc.math.geom.Vec2
import arc.struct.Seq
import arc.util.Time
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import mindustry.content.Bullets
import mindustry.content.UnitTypes
import mindustry.entities.bullet.BulletType
import mindustry_plugin_utils.Logger
import the_worst_one.cfg.Reloadable
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Unit
import mindustry.gen.Groups
import mindustry.type.Item
import mindustry.type.UnitType
import mindustry.type.Weapon
import mindustry_plugin_utils.Fs
import the_worst_one.cfg.Globals
import the_worst_one.game.commands.Discord
import java.io.File
import java.lang.Exception
import java.util.*
import java.util.function.Consumer


class PewPew(val logger: Logger, val users: Users, override var configPath: String) : Reloadable {

    val state = HashMap<Unit, State>()
    private val pool = Seq<State>()
    val weaponSets = HashMap<UnitType, HashMap<Item, Weapon>>()

    init {
        logger.run(EventType.Trigger.update) {
            update()
        }

        logger.on(EventType.UnitDestroyEvent::class.java) {
            val state = state.remove(it.unit)
            if (state != null) {
                state.reset()
                pool.add(state)
            }
        }

        logger.on(EventType.GameOverEvent::class.java) {
            for (state in state.values) {
                state.reset()
                pool.add(state)
            }
            state.clear()
        }
    }

    private fun update() {
        for(u in Groups.unit) {
            val unit = u ?: continue
            if(!unit.hasItem()) continue
            val set = weaponSets[unit.type] ?: continue
            val weapon = set[unit.stack.item] ?: continue
            val state = state.computeIfAbsent(unit) { pool.pop { State() } }
            state.reload += Time.delta / 60f
            if(unit.isShooting) {
                weapon.shoot(unit, state)
            }
        }
    }

    class Stats (
        val bullet: String = "standardCopper", //bullet type
        val inaccuracy: Float = 2f, // in degrees
        val damageMultiplier: Float = 1f,
        val reload: Float = .3f, // in seconds
        val bulletsPerShot: Int = 2,
        val ammoMultiplier: Int = 4,
        val itemsPerScoop: Int = 1,
    )

    class Weapon(val stats: Stats, ut: UnitType? = null, name: String = "ambiguos") {
        var bullet: BulletType = try {
            if (stats.bullet.contains("-")) {
                Globals.unitBullet(stats.bullet, ut)
            } else {
                Globals.bullet(stats.bullet) ?: throw Exception("weapon '${stats.bullet}' does not exist" )
            }
        } catch (e: Exception) {
            throw Exception("weapon '$name' is invalid: ${e.message}")
        }

        private lateinit var original: BulletType

        init {
            // finding bullet with biggest range
            ut?.weapons?.forEach {
                if (!this::original.isInitialized || original.range() < it.bullet.range()) {
                    original = it.bullet
                }
            }

            if(!this::original.isInitialized) {
                original = bullet
            }
        }

        fun shoot(unit: Unit, state: State) {
            if (state.reload < stats.reload) {
                println(6)
                return
            }
            state.reload = 0f

            // refilling ammo
            if (state.ammo == 0) {
                // not enough items to get new ammo
                if (unit.stack.amount < stats.itemsPerScoop) {
                    return
                }
                unit.stack.amount -= stats.itemsPerScoop
                state.ammo += stats.ammoMultiplier
            }
            state.ammo--

            shoot(h4.set(unit.aimX, unit.aimY), h5.set(unit.x, unit.y), unit.vel, unit.team, state)
        }

        fun shoot(aim: Vec2, pos: Vec2, vel: Vec2, team: Team, d: State) {
            h1
                .set(original.range(), 0f) // set length to range
                .rotate(h2.set(aim).sub(pos).angle()) // rotate to shooting direction
                .add(h3.set(vel).scl(60f * Time.delta)) // add velocity offset

            // its math
            val velLen = h1.len() / original.lifetime / bullet.speed
            var life = original.lifetime / bullet.lifetime
            val dir = h1.angle()
            if (!bullet.collides) {
                // h2 is already in state of vector from u.pos to u.aim and we only care about length
                life *= (h2.len() / bullet.range()).coerceAtMost(1f) // bullet is controlled by cursor
            }
            for (i in 0 until stats.bulletsPerShot) {
                Call.createBullet(
                    bullet,
                    team,
                    pos.x, pos.y,
                    dir + Mathf.range(-stats.inaccuracy, stats.inaccuracy),  // apply inaccuracy
                    stats.damageMultiplier * bullet.damage,
                    velLen,
                    life
                )
            }
        }

        companion object {
            // helper vectors to reduce allocations.
            var h1 = Vec2()
            var h2 = Vec2()
            var h3 = Vec2()
            var h4 = Vec2()
            var h5 = Vec2()
        }


    }

    class State {
        var ammo = 0
        var reload = 0f

        fun reset() {
            ammo = 0
            reload = 0f
        }
    }

    override fun reload() {
        weaponSets.clear()
        try {
            val config =  Klaxon().parse<Map<String, Map<String, JsonObject>>>(File(configPath))!!
            for ((k, set) in config) {
                val unit = Globals.unit(k) ?: throw Exception("unit '$k' does not exits")
                if(unit.weapons.isEmpty) throw Exception("unit '$k' has no weapons thus it cannot hold any extra")
                val map = HashMap<Item, Weapon>()
                for ((i, s) in set) {
                    val stats = Klaxon().parseFromJsonObject<Stats>(s)!!
                    val item = Globals.item(i) ?: throw Exception("unit '$k' contains unknown item '$i'")
                    map[item] = Weapon(stats, unit, "$k-$i")
                }
                weaponSets[unit] = map
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Globals.loadFailMessage("pewpew", e)
            Fs.createDefault(configPath, mapOf(
                "alpha" to mapOf(
                    "copper" to Stats()
                )
            ))
        }


    }
}