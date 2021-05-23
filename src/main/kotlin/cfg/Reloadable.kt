package cfg

import mindustry_plugin_utils.Enums
import mindustry_plugin_utils.Json
import java.io.File

interface Reloadable {
    fun reload()
    val configPath: String

    val view get() = try {
        File(configPath).readText()
    } catch (e: Exception) {
        "unsupported"
    }

    fun modify(method: String, type: String, path: String, value: String): String {
        val m = Enums.contains(Json.Method::class.java, method) ?: return "method"
        val t = Enums.contains(Json.Type::class.java, type) ?: return "type"
        return try {
            val j = Json(File(configPath).readText())
            val r = j.modify(m as Json.Method, t as Json.Type, path, value)
            File(configPath).writeText(j.toString())
            r
        } catch(e: Exception) {
            "osError"
        }
    }
}