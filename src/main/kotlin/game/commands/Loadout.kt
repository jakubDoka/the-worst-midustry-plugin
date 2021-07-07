package game.commands

import cfg.Globals
import db.Ranks
import game.Interpreter
import game.Loadout
import game.Voting
import java.lang.Exception

class Loadout(val loadout: Loadout, val voting: Voting): Command("loadout") {
    val store = Voting.Session.Data(1, 5, "store", "loadout", Ranks.Perm.Store)
    val load = Voting.Session.Data(1, 5, "load", "loadout", Ranks.Perm.Load)

    override fun run(args: Array<String>): Enum<*> {
        if(args.size == 3) {
            if(notNum(1, args)) {
                return Generic.NotAInteger
            }
            val amount = num(args[1])

            when(args[0]) {
                "load" -> {
                    val item = Globals.itemList().find { it.name == args[2] }
                    if (item == null) {
                        send("loadout.invalidItem", Globals.listItems())
                        return Result.InvalidItem
                    }
                }
                "store" -> {
                    try {
                        Interpreter.run(args[2], mapOf("itemName" to "", "itemAmount" to "")) // syntax check
                        voting.add(Voting.Session(store, user!!, args[1],args[2]) {
                            loadout.launch(args[2], amount)
                        })
                    } catch (e: Exception) {
                        send("loadout.store.error", e.message ?: "congrats, even error cannot be displayed")
                    }
                }
                else -> {
                    send("wrongOption", "load store")
                    return Generic.Mismatch
                }
            }
        } else if (args.isEmpty()) {
            alert("loadout.title", "placeholder", loadout.items.format())
        } else {
            send("loadout.count")
            return Generic.NotEnough
        }

        return Generic.Success
    }

    enum class Result {
        InvalidItem
    }
}