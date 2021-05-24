package cfg

import mindustry.content.UnitTypes
import mindustry.type.UnitType
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.kotlinProperty

object Globals {
    var testing = false

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
}