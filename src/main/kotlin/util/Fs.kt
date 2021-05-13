package util

import com.beust.klaxon.Klaxon
import mindustry_plugin_utils.Logger
import java.io.File

object Fs {
    fun <T> createDefault(path: String, value: T) {
        try {
            val f = File(path)
            f.mkdir()
            f.createNewFile()
            f.setWritable(true)
            f.writeText(Klaxon().toJsonString(value))
        } catch (e: Exception) {
            println("Failed to create default config $path")
            e.printStackTrace()
        }
    }
}