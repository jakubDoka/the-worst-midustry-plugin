package game.u

import db.Driver
import db.Ranks
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry_plugin_utils.Templates

class User(val inner: Player, val data: Driver.RawUser, val testing: Boolean = false) {
    companion object {
        val prefix = "[coral][[[scarlet]Server[]]:[#cbcbcb] "
    }

    init {
        if(paralyzed) {
            inner.name += data.rank.display
        } else {
            inner.name = data.name + data.rank.display + "[gray]#" + data.id
        }
    }

    val paralyzed: Boolean get() = -1L == data.id

    fun alert(title: String, bundleKey: String, vararg arguments: Any, color: String = "orange") {
        alert(Templates.info(title, translate(bundleKey, *arguments), color))
    }

    fun alert(plainText: String) {
        if(testing)
            println(plainText)
        else
            Call.infoMessage(inner.con, plainText)
    }

    fun send(key: String, vararg args: Any) {
        sendLabeled(translate(key, *args))
    }

    fun sendLabeled(plainText: String) {
        sendPlain(prefix + plainText)
    }

    fun sendPlain(plainText: String) {
        if(testing)
            println(plainText)
        else
            inner.sendMessage(plainText)
    }

    fun translateOr(key: String, paramText: String): String {
        if(data.bundle.missing(key)) {
            return paramText
        }
        return translate(key)
    }

    fun translate(key: String, vararg arguments: Any): String {
        return String.format(data.bundle.get(key), *arguments)
    }

    fun idSpectator(): Boolean {
        return data.rank.control == Ranks.Control.None
    }


}