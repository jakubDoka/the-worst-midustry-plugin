package game

import cfg.Globals
import cfg.Globals.ensure
import cfg.Reloadable
import com.beust.klaxon.Klaxon
import mindustry.Vars
import mindustry.game.Team
import mindustry.type.Item
import mindustry_plugin_utils.Fs
import mindustry_plugin_utils.Messenger
import java.io.File
import java.lang.Long.min
import java.lang.StringBuilder

class Loadout(override val configPath: String) : Reloadable {
    var items = Data()

    var messenger = Messenger("loadout")

    init {
        reload()
    }

    fun launch(condition: String, amount: Long) {
        val core = Vars.state.teams.get(Team.sharded).core() ?: return

        for(i in Globals.itemList()) {
            val coreAmount = core.items.get(i).toLong()
            if(Interpreter.run(condition, mapOf("itemName" to i.name, "itemAmount" to amount)) as Boolean) {
                val toSend = min(amount, coreAmount)
                items.inc(i, toSend)
                core.items.remove(i, toSend.toInt())
            }
        }

        save()
    }

    fun save() {
        try {
            val file = File(configPath)
            file.ensure()
            file.delete()
            Fs.createDefault(configPath, items)
        } catch (e: Exception) {
            e.printStackTrace()
            messenger.log("Failed to save items.")
        }
    }

    override fun reload() {
        try {
            val raw = Klaxon().parse<Map<String, Long>>(File(configPath))!!
            items.clear()
            for((k, v) in raw) items[k] = v
        } catch (e: Exception) {
            e.printStackTrace()
            messenger.log("Failed to load items.")
            save()
        }
    }

    class Data: HashMap<String, Long>() {
        fun value(item: Item): Long {
            return computeIfAbsent(item.name) { 0 }
        }

        fun dec(item: Item, delta: Long) {
            inc(item, -delta)
        }

        fun inc(item: Item, delta: Long) {
            put(item.name, getOrDefault(item.name, 0) + delta)
        }

        fun format(): String {
            val sb = StringBuilder()
            for(i in Globals.itemList()) {
                sb
                    .append(Globals.itemIcons[i.name]!!)
                    .append(getOrDefault(i.name, 0))
                    .append("\n")
            }
            return sb.toString()
        }
    }
}