package the_worst_one.game.u

import the_worst_one.cfg.Globals
import the_worst_one.db.Driver
import the_worst_one.game.Pets
import the_worst_one.game.Users
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.gen.Unit
import mindustry_plugin_utils.Templates

class User(val inner: Player, val data: Driver.RawUser, previous: Driver.RawUser? = null) {
    companion object {
        val prefix = "[coral][[[scarlet]Server[]]:[#cbcbcb] "
    }

    var afkPoints: Int = 0
    var mount: Unit? = null
    val pets = mutableListOf<Pets.Pet>()

    val paralyzed: Boolean get() = -1L == data.id

    init {
        if(paralyzed) {
            inner.name += data.rank.display
        } else {
            initData(previous)
        }
    }

    fun initData(previous: Driver.RawUser? = null) {
        inner.name = data.name.replace(" ", "") + data.display.display + "[gray]#" + data.id + "[white]"
        inner.admin = data.admin()
        data.ip = inner.con.address
        data.uuid = inner.uuid()
        data.lastCommand = previous?.lastCommand ?: 0L
    }

    fun alert(titleKey: String, bundleKey: String, vararg arguments: Any, color: String = "orange") {
        alert(Templates.info(data.translate(titleKey), data.translate(bundleKey, *arguments), color))
    }

    fun alert(plainText: String) {
        if(Globals.testing)
            println(Templates.cleanColors(plainText))
        else
            Call.infoMessage(inner.con, plainText)
    }

    fun send(key: String, vararg args: Any) {
        sendLabeled(data.translate(key, *args))
    }

    fun sendLabeled(plainText: String) {
        sendPlain(prefix + plainText)
    }

    fun sendPlain(plainText: String) {
        if(Globals.testing)
            println(Templates.cleanColors(plainText))
        else
            inner.sendMessage(plainText)
    }

    fun disconnect(users: Users) {
        mount?.kill()
        users.save(this)
    }
}