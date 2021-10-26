package the_worst_one.cfg

import arc.util.Strings
import io.netty.channel.ChannelFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import mindustry.content.Bullets
import mindustry.content.UnitTypes
import mindustry.entities.Effect
import mindustry.type.UnitType
import mindustry_plugin_utils.Templates
import reactor.core.publisher.Mono
import reactor.netty.ByteBufMono
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.HttpClientResponse
import java.io.File
import java.util.function.BiFunction
import mindustry.content.Fx
import mindustry.content.Items
import mindustry.entities.bullet.BulletType
import mindustry.type.Item
import mindustry_plugin_utils.Logger
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import java.lang.management.ThreadInfo

import java.lang.management.ManagementFactory
import java.lang.management.ThreadMXBean


// most of the content should be moved to utils
object Globals {
    var testing = false
    const val root = "config/mods/worst/"
    const val botRoot = root + "bot/"
    const val coreIcon = "\uF869"
    const val hudDelimiter = "[gray]|[]"

    val logger = Logger(root + "logger/config.json")


    // the magic it self
    val itemIcons = mapOf(
        "scrap" to "\uf830" ,
        "copper" to "\uf838" ,
        "lead" to "\uf837" ,
        "graphite" to "\uf835" ,
        "coal" to "\uf833" ,
        "titanium" to "\uf832" ,
        "thorium" to "\uf831" ,
        "silicon" to "\uf82f" ,
        "plastanium" to "\uf82e" ,
        "phase-fabric" to "\uf82d" ,
        "surge-alloy" to "\uf82c" ,
        "spore-pod" to "\uf82b" ,
        "sand" to "\uf834" ,
        "blast-compound" to "\uf82a" ,
        "pyratite" to "\uf829" ,
        "metaglass" to "\uf836" ,
    )

    fun loadFailMessage(source: String, e: Throwable) {
        println("$source:: failed to load the config file: ${e.message}")
    }

    fun File.ensure() {
        if(!exists()) {
            parentFile.mkdirs()
            createNewFile()
        }
    }

    fun Boolean.orRun(fn: () -> Unit) {
        if (!this) {
            fn()
        }
    }

    fun unit(name: String): UnitType? {
        return property(name, UnitTypes::class.java) as? UnitType?
    }

    fun effect(name: String): Effect? {
        return property(name, Fx::class.java) as? Effect?
    }

    fun bullet(name: String): BulletType? {
        return property(name, Bullets::class.java) as? BulletType?
    }

    fun item(name: String): Item? {
        return itemList().find { it.name == name }
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

    fun effectListString(): String {
        return Fx::class.java.fields.joinTo(StringBuilder(), " ") { it.name }.toString()
    }

    private lateinit var itemString: String
    fun listItems(): String {
        if(!this::itemString.isInitialized) {
            itemString = itemList().joinTo(StringBuilder(), "") {
                "[#${it.color}]${it.name}[]"
            }.toString()
        }
        return itemString
    }

    private lateinit var items: List<Item>
    fun itemList(): List<Item> {
        if(!this::items.isInitialized) {
            items = propertyList(Items::class.java).map { it as Item }
        }
        return items
    }

    fun propertyList(target: Class<*>): List<Any> {
        return target.declaredFields.map { property(it.name, target)!! }
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

    fun <V> downloadAttachment(url: String, fn: BiFunction<in HttpClientResponse, in ByteBufMono, out Mono<V>>): V {
        return HttpClient.create().get().uri(url).responseSingle(fn).block()!!
    }

    fun KClass<*>.property(name: String): KProperty1<out Any, *>? {
        for(p in declaredMemberProperties) {
            if(p.name.equals(name, ignoreCase = true)) {
                return p
            }
        }

        return null
    }

    val timeUnitSuffixes = listOf("y", "d", "h", "m", "s")

    // i know this is inaccurate as fuck, but i also do not care
    fun Long.time(): String {
        val amounts = listOf(
            (this / (1000 * 60 * 60 * 24 * 365L)),
            (this / (1000 * 60 * 60 * 24L)) % 365,
            (this / (1000 * 60 * 60L)) % 24,
            (this / (1000 * 60L)) % 60,
            (this / 1000L) % 60,
        )

        val sb = StringBuilder()
        for(i in amounts.indices) {
            val amount = amounts[i]
            if(amount != 0L) {
                sb
                    .append(amount)
                    .append(timeUnitSuffixes[i])
            }
        }

        return sb.toString()
    }

    private val channel = Channel<() -> Unit>()

    init {
        Globals.runLoggedGlobalScope { coroutineScope {
            while (true) {
                val fn = channel.receive()
                launch {
                    fn()
                }
            }
        } }

        Globals.runLoggedGlobalScope {
            while(true) {
                val bean: ThreadMXBean = ManagementFactory.getThreadMXBean()
                val threadIds: LongArray? = bean.findDeadlockedThreads()

                if (threadIds == null) {
                    delay(1000 * 10)
                    continue
                }

                val infos: Array<ThreadInfo> = bean.getThreadInfo(threadIds)
                for (info in infos) {
                    println("Deadlock on thread ${info.threadId}")
                    for(elem in info.stackTrace) {
                        print(elem)
                    }
                }
            }
        }
    }

    fun runLoggedGlobalScope(forever: Boolean = true, fn: suspend () -> Unit) {
        GlobalScope.launch {
            do {
                logger.run { runBlocking { fn() } }
            } while (forever)
        }
    }

    fun run(fn: () -> Unit) {
        runBlocking { channel.send(fn) }
    }

    fun unitBullet(ptr: String, unit: UnitType?): BulletType {
        var ut = unit
        val parts = ptr.split("-")
        if (parts.size != 2) {
            throw Exception("the unit bullet has to be unit name, and weapon index separated by '-'")
        }

        if (parts[0] != "self") {
            ut = unit(parts[0]) ?: throw Exception("unit '${parts[0]}' does not exist")
        } else if(ut == null) {
            throw Exception("cannot use 'self' here")
        }

        if (!Strings.canParsePositiveInt(parts[1])) {
            throw Exception("cannot parse ${parts[1]} to integer")
        }
        val idx = parts[1].toInt() - 1
        if (idx >= ut.weapons.size || idx < 0) {
            throw Exception("the maximal weapon is ${ut.weapons.size} and min is 1, you entered ${parts[1]}")
        }

        return ut.weapons[idx].bullet
    }

    interface Log {
        val prefix: String
        fun <T> log(value: T) {
            print("$prefix::")
            println(value)
        }
    }
}