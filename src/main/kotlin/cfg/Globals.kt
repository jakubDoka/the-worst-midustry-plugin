package cfg

import db.Driver
import mindustry.content.UnitTypes
import mindustry.type.UnitType
import mindustry_plugin_utils.Templates
import java.io.File
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.kotlinProperty

object Globals {
    var testing = false
    val root = "config/mods/worst/"

    fun listUnits(): String {
        return propertyList(UnitTypes::class.java)
    }

    fun unit(name: String): UnitType? {
        return property(name, UnitTypes::class.java) as? UnitType?
    }

    fun property(name: String, target: Class<*>, obj: Any? = null): Any? {
        return try {
            val field = target.getDeclaredField(name)
            field.get(obj)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun propertyList(target: Class<*>): String {
        return target.declaredFields.joinTo(StringBuilder(), " ") { it.name }.toString()
    }

    fun message(name: String, message: String): String {
        return String.format("%s [#aaaaaa]>[] %s", name, message)
    }

    fun discordMessage(name: String, message: String): String {
        return Templates.cleanColors(String.format("**%s >** %s", name, message))
    }

    fun mapSiActive(fileName: String): Boolean {
        return File("config/maps/${fileName}").exists()
    }
}