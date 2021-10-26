package the_worst_one.db

import arc.struct.Seq
import arc.util.Time
import the_worst_one.bundle.Bundle
import the_worst_one.cfg.Globals
import the_worst_one.cfg.Globals.ensure
import the_worst_one.cfg.Reloadable
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import the_worst_one.game.Interpreter
import kotlinx.coroutines.runBlocking
import mindustry.Vars
import mindustry.game.Team
import mindustry.gen.Player
import mindustry.type.Item
import mindustry_plugin_utils.Fs
import mindustry_plugin_utils.Templates
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.sql.ResultSet
import java.util.*
import kotlin.math.max

// Driver handles all database calls and holds information about the_worst_one.db structure
class Driver(override var configPath: String = "config/driver.json", val ranks: Ranks = Ranks()): Reloadable {
    var config = Config()
    private lateinit var con: Database
    private val outlook = Lookout()
    val users = UserManager(ranks, outlook, this)
    val maps = MapManager()
    val items = ItemManager()

    override fun reload() {
        if(this::con.isInitialized) {
            TransactionManager.currentOrNull()?.connection?.close()
            TransactionManager.resetCurrent(null)
        }

        try {
            config = Klaxon().parse<Config>(File(configPath))!!
        } catch (e: Exception) {
            e.printStackTrace()
            Globals.loadFailMessage("driver", e)
            Fs.createDefault(configPath, config)
        }

        println("driver:: connecting to database...")
        val url = String.format("jdbc:postgresql:%s", config.database)
        con = Database.connect(url, user = config.user, password = config.password)
        println("driver:: connected")

        drop()
    }

    fun drop() {
        transaction {
            if(Globals.testing) SchemaUtils.drop(Users, Bans, Progress, Maps, Items)
            SchemaUtils.create(Users, Bans, Progress, Maps, Items)
            transaction {
                exec("create index if not exists points on Users (points desc)")
            }
        }
    }

    // fmtRS formats ResultRow
    fun fmtRS(rs: ResultSet): String {
        val sb = StringBuilder()
        val md = rs.metaData
        val len = md.columnCount

        while(rs.next()) {
            for (i in 1..len) {
                sb
                    .append("[gray]")
                    .append(md.getColumnName(i))
                    .append(":[] ")
                    .append(rs.getString(i))
                    .append("\n")
            }
        }

        return sb.toString()
    }

    // Exec executes sql command
    fun exec(command: String): String {
        return transaction {
            var str = "nothing"
            exec(command) {
                str = fmtRS(it)
            }
            str
        }
    }

    companion object {
        // Subnet returns subnet part of ip address
        fun String.subnet(): String {
            if (!contains(".")) return ""
            return substring(0, lastIndexOf('.'))
        }
    }

    // login logs player into different account
    fun login(id: Long, player: Player) {
        transaction {
            Users.update({ Users.id eq id }){
                it[uuid] = player.uuid()
                it[ip] = player.con.address
            }
        }
    }

    // logout disconnects player from his account if there are other matching accounts
    fun logout(user: RawUser) {
        transaction {
            if(user.password == Users.noPassword) {
                Users.deleteWhere { Users.id eq user.id }
            } else {
                Users.update({ Users.id eq user.id }){
                    it[ip] = "logged.off"
                }
                user.save(config.multiplier, ranks)
            }
        }
    }

    class UserManager(val ranks: Ranks, val outlook: Lookout, val driver: Driver) {
        fun exists(id: Long): Boolean {
            return transaction { !Users.slice(Users.id).select{Users.id eq id}.empty() }
        }

        // newUser creates user with set and done name, ip address and uuid can be changed,
        // newly created user is also passed to lookout to localize him and find out a best the_worst_one.bundle
        fun new(player: Player): RawUser {
            return transaction {
                val time = Time.millis().toString()
                val id = Users.insertAndGetId {
                    it[uuid] = if(Globals.testing) time else player.uuid()
                    it[ip] = player.con.address
                    it[name] = player.name
                    it[bornDate] = Time.millis()
                }.value

                Progress.insert {
                    it[owner] = id
                }

                runBlocking {
                    outlook.input.send(Lookout.Request(player.con.address, id, player.locale))
                }

                val u = load(id)
                if (Globals.testing) u.uuid = time
                u
            }
        }

        // loadUser loads a user by id
        fun load(id: Long): RawUser {
            return transaction {
                RawUser(ranks, Users.select { Users.id eq id }.first())
            }
        }

        // loadUser loads a user by id
        fun load(id: String): RawUser? {
            return transaction {
                val query = Users.select { Users.discord eq id }
                if (query.empty()) null else RawUser(ranks, query.first())
            }
        }

        fun search(player: Player): Seq<RawUser> {
            val res = search(Op.build { Users.uuid.eq(player.uuid()) and Users.ip.eq(player.con.address) } )
            if(res.size == 0) {
                return search(Op.build { Users.uuid.eq(player.uuid()) or Users.ip.eq(player.con.address) } )
            }
            return res
        }

        // searchUser
        private fun search(op: Op<Boolean>): Seq<RawUser> {
            return transaction {
                val values = Seq<RawUser>()
                Users.select { op }.forEach{ values.add(RawUser(ranks, it)) }
                values
            }
        }

        fun save(data: RawUser) {
            data.stats.save(data.id)
        }

        fun <T> get(id: Long, c: Column<T>): T? {
            return transaction {
                val query = Users.slice(c).select {Users.id eq id}
                if (query.empty()) null else query.first()[c]
            }
        }

        fun <T> set(id: Long, c: Column<T>, value: T) {
            return transaction {
                Users.update({ Users.id eq id}) {
                    it[c] = value
                }
            }
        }
    }

    // Raw USer stores data about user retrieved from database. This data is critical and retrieving
    // it from database each time would affect performance. Data has to be saved when player disconnects
    class RawUser(ranks: Ranks, row: ResultRow? = null) {
        var id = row?.get(Users.id)?.value ?: -1L

        var name = row?.get(Users.name) ?: "unknown"
        var uuid = row?.get(Users.uuid) ?: ""
        var ip = row?.get(Users.ip) ?: ""
        var discord = row?.get(Users.discord) ?: Users.noDiscord
        var password = row?.get(Users.password) ?: Users.noPassword
        var muted = row?.get(Users.muted)?.split(" ")?.filter { it != "" }?.map { it.toLong() }?.toMutableSet() ?: mutableSetOf()
        var mutedForAll = row?.get(Users.mutedForAll) ?: false

        var rank = ranks[row?.get(Users.rank)] ?: ranks.paralyzed
        var display = ranks[row?.get(Users.display)] ?: rank
        val specials = HashSet(row?.get(Users.specials)?.split(" ") ?: listOf())
        val stats = Stats(id)
        val age = Time.millis() - (row?.get(Users.bornDate) ?: 0)

        val country = row?.get(Users.country) ?: "unknown"
        val bundle = Bundle(row?.get(Users.locale) ?: "en_US")

        val commandRateLimit = 30 / ((stats.playTime / (1000 * 60 * 60)) + 1) + 3
        var lastCommand: Long = 0L

        init {
            if(!specials.contains(display.name) || rank.control.spectator()) {
                display = rank
            }
        }

        fun admin(): Boolean {
            return rank.control.admin() || display.control.admin()
        }

        fun spectator(): Boolean {
            return rank.control.spectator() || display.control.spectator()
        }

        fun points(ranks: Ranks, multiplier: Stats): Long {
            return stats.points(multiplier) + rankValue(ranks)
        }

        fun rankValue(ranks: Ranks): Long {
            var total = 0L
            for(r in specials) {
                total += ranks[r]?.value ?: 0
            }
            return total
        }

        val voteValue get() = if(rank.control.spectator())
            rank.voteValue
        else
            max(rank.voteValue, display.voteValue)

        fun save(multiplier: Stats, ranks: Ranks) {
            stats.save(id)
            transaction {
                Users.update({ Users.id eq this@RawUser.id }) {
                    it[specials] = this@RawUser.specials.joinTo(StringBuilder(), " ").toString()
                    it[muted] = this@RawUser.muted.joinTo(StringBuilder(), " ").toString()
                    it[mutedForAll] = this@RawUser.mutedForAll
                    it[name] = this@RawUser.name
                    it[discord] = this@RawUser.discord
                    it[password] = this@RawUser.password
                    it[rank] = this@RawUser.rank.name
                    it[display] = this@RawUser.display.name
                    it[points] = points(ranks, multiplier)
                    it[ip] = this@RawUser.ip
                    it[uuid] = this@RawUser.uuid
                }
            }
        }

        fun hasPerm(perm: Ranks.Perm): Boolean {
            return rank.perms.contains(perm) || display.perms.contains(perm)
        }

        fun owns(rank: String): Boolean {
            return rank == this.rank.name || specials.contains(rank)
        }

        fun translateOr(key: String, o: String): String {
            return bundle.translateOr(key, o)
        }

        fun translate(key: String, vararg args: Any): String {
            return bundle.translate(key, *args)
        }

        fun idName(): String {
            return "$name[gray]#$id[]"
        }

        // banned returns whether player is banned on this server
        fun banned(): Boolean {
            return transaction { banned(uuid) || banned(ip.subnet()) }
        }

        // ban bans the player
        fun ban() {
            transaction {
                ban(uuid)
                ban(ip)
            }
        }

        fun unban() {
            transaction { Bans.deleteWhere {Bans.value.eq(uuid) or Bans.value.eq(ip)} }
        }

        // ban adds ip or uuid to death note
        fun ban(value: String) {
            transaction { if(!banned(value)) Bans.insert { it[Bans.value] = value } }
        }

        // banned returns whether siring is in death note
        fun banned(value: String): Boolean {
            return transaction { !Bans.select {Bans.value eq value}.empty() }
        }

        fun colorMessage(message: String): String {
            val colors = display.color.split(" ")
            return if(colors.size > 1)
                Templates.transition(message, *Array(colors.size){colors[it]}, density = 2)
            else
                "[${colors[0]}]${message}"
        }
    }

    // Config holds database config
    class Config(
        var user: String = "postgres",
        var password: String = "twstest123",
        var database: String = "gee",
        var verbose: Boolean = false,
        val multiplier: Stats = Stats(),
    )

    // Users is definition of user table (Exposed framework macro)
    object Users : LongIdTable() {
        const val noDiscord = "none"
        const val noPassword = "none"
        const val defaultRank = "newcomer"
        const val noPremium = "none"
        const val defaultCountry = "unknown"
        const val defaultLocale = "en_Us"

        val name = text("name")
        val uuid = text("uuid").index()
        val discord = text("discord").index().default(noDiscord)
        val ip = text("ip").index()
        val bornDate = long("bornDate")
        val muted = text("muted").default("")
        val mutedForAll = bool("mutedForAll").default(false)

        val password = text("password").default(noPassword)
        val rank = text("rank").default(defaultRank)
        val specials = text("specials").default("")
        val premium = text("premium").default(noPremium)
        val display = text("display").default("")

        val country = text("country").default(defaultCountry)
        val locale = text("locale").default(defaultLocale)

        val points = long("points").index().default(0)

        val credits = long("credits").default(0)
        val webToken = text("webToken").default("")
    }

    // Bas is definition of bans table (Exposed framework macro)
    object Bans: Table() {
        val value = text("value").uniqueIndex()
        override val primaryKey = PrimaryKey(value)
    }

    // Progress stored all common stats obtained by playing the_worst_one.game casually
    object Progress: Table() {
        val owner = long("owner")
        val playTime = long("playTime").default(0)
        val silence = long("silence").default(0)
        val built = long("built").default(0)
        val destroyed = long("destroyed").default(0)
        val killed = long("killed").default(0)
        val deaths = long("deaths").default(0)
        val played = long("played").default(0)
        val wins = long("wins").default(0)
        val messages = long("messages").default(0)
        val commands = long("commands").default(0)
        val lastDeath = long("lastDeath").default(0)
        override val primaryKey = PrimaryKey(owner)
    }

    // Stats is ram representation of progress and is used to increase the counter.
    // When player disconnects, new values are saved to database
    class Stats(owner: Long = -1) {
        @Json(ignored = true)
        val joined = Time.millis()
        @Json(ignored = true)
        var lastMessage = joined

        var built: Long = 0
        var destroyed: Long = 0
        var killed: Long = 0
        var deaths: Long = 0
        var played: Long = 0
        var wins: Long = 0
        var messages: Long = 0
        var commands: Long = 0
        var playTime: Long = 0
        var silence: Long = 0
        var lastDeath: Long = 0

        init {
            if(owner != -1L) {
                transaction {
                    val row = Progress.select { Progress.owner eq owner }.first()
                    built = row[Progress.built]
                    destroyed = row[Progress.destroyed]
                    killed = row[Progress.killed]
                    deaths = row[Progress.deaths]
                    played = row[Progress.played]
                    wins = row[Progress.wins]
                    messages = row[Progress.messages]
                    commands = row[Progress.commands]
                    playTime = row[Progress.playTime]
                    silence = row[Progress.silence]
                    lastDeath = row[Progress.lastDeath]
                }
            }
        }

        fun points(multiplier: Stats): Long {
            return (
                    built * multiplier.built +
                            destroyed * multiplier.destroyed +
                            killed * multiplier.killed +
                            played * multiplier.played +
                            wins * multiplier.wins +
                            messages * multiplier.messages +
                            commands * multiplier.commands +
                            playTime / (multiplier.playTime + 1) +
                            silence / (multiplier.silence + 1)
                    )
        }

        fun save(owner: Long) {
            val duration = Time.millis() - joined
            val newPlayTime = playTime + duration
            val newSilence = if(lastMessage == joined) silence + duration else Time.millis() - lastMessage
            transaction {
                Progress.update({Progress.owner eq owner}) {
                    it[built] = this@Stats.built
                    it[destroyed] = this@Stats.destroyed
                    it[killed] = this@Stats.killed
                    it[deaths] = this@Stats.deaths
                    it[played] = this@Stats.played
                    it[wins] = this@Stats.wins
                    it[messages] = this@Stats.messages
                    it[commands] = this@Stats.commands
                    it[playTime] = newPlayTime
                    it[silence] = newSilence
                    it[lastDeath] = this@Stats.lastDeath
                }
            }
        }

        fun onMessage() {
            lastMessage = Time.millis()
        }

        fun onDeath() {
            lastDeath = Time.millis()
        }
    }

    object Maps: LongIdTable() {
        val name = text("name")
        val author = text("author")
        val played = long("played").default(0)
        val won = long("won").default(0)
        val fileName = text("fileName").index("fileName")
        val file = binary("file")
    }

    class MapData(row: ResultRow, loadFile: Boolean = false) {
        val name = row[Maps.name]
        val author = row[Maps.author]
        val played = row[Maps.played]
        val won = row[Maps.won]
        val fileName = row[Maps.fileName]
        val file = if(loadFile) row[Maps.file] else null
    }

    class MiniMapData(row: ResultRow) {
        val active = Globals.mapSiActive(row[Maps.fileName])
        val name = row[Maps.name]
        val id = row[Maps.id]
    }

    class MapManager {
        fun loadMapDataContains(name: String): MapData? {
            return loadMapData { Maps.name.like("%$name%") }
        }

        fun loadMapData(name: String): MapData? {
            return loadMapData { Maps.name eq name }
        }

        fun loadMapData(id: Long): MapData? {
            return loadMapData { Maps.id eq id}
        }

        fun loadMapList(): List<MiniMapData> {
            return transaction {
                val list = ArrayList<MiniMapData>()
                Maps.slice(Maps.name, Maps.id, Maps.fileName).selectAll().orderBy(Maps.id).forEach {
                    list.add(MiniMapData(it))
                }
                list
            }
        }

        private fun loadMapData(cond: SqlExpressionBuilder.() -> Op<Boolean>): MapData? {
            return transaction {
                val q = Maps.slice(Maps.name, Maps.author, Maps.played, Maps.won, Maps.fileName).select(cond)
                if (q.empty()) {
                    null
                } else {
                    MapData(q.first())
                }
            }
        }

        fun add(map: mindustry.maps.Map): Long {
            return transaction {
                if(update(Long.MAX_VALUE, map)) Long.MAX_VALUE
                else Maps.insertAndGetId {
                    it[name] = map.name()
                    it[author] = map.author()
                    it[file] = map.file.readBytes()
                    it[fileName] = map.file.name()
                }.value
            }
        }

        fun update(id: Long, map: mindustry.maps.Map): Boolean {
            return transaction {
                Maps.update({ Maps.id.eq(id) or Maps.fileName.eq(map.file.name()) }) {
                    it[name] = map.name()
                    it[author] = map.author()
                    it[file] = map.file.readBytes()
                    it[fileName] = map.file.name()
                } == 1
            }
        }

        fun remove(id: Long): Boolean {
            return transaction { Maps.deleteWhere { Maps.id eq id } == 1 }
        }

        fun activate(id: Long) {
            val map = transaction { Maps.slice(Maps.file, Maps.fileName).select { Maps.id eq id }.first() }
            val dest = File("config/maps/${map[Maps.fileName]}")
            dest.ensure()
            dest.writeBytes(map[Maps.file])
            reload()
        }

        fun deactivate(id: Long) {
            val map = transaction { Maps.slice(Maps.fileName).select { Maps.id eq id }.first() }
            if (!File("config/maps/${map[Maps.fileName]}").delete()) {
                throw RuntimeException("Already deactivated.")
            }
            reload()
        }

        fun exists(id: Long): Boolean {
            return transaction { Maps.slice(Maps.id).select { Maps.id eq id }.count() != 0L }
        }

        fun reload() {
            if(!Globals.testing) Vars.maps.reload()
        }
    }

    object Items: Table() {
        val name = text("name").uniqueIndex()
        val amount = long("amount").default(0)
    }

    class ItemManager {
        fun take(items: Map<String, Long>) {
            transaction {
                for((k, v) in items) {
                    Items.update({ Items.name eq k }) {
                        with(SqlExpressionBuilder) {
                            it.update(amount, amount - v)
                        }
                    }
                }
            }
        }

        fun findMissing(items: Map<String, Long>): Map<String, Long> {
            val missing = mutableMapOf<String, Long>()

            transaction {
                Items.selectAll().forEach {
                    val name = it[Items.name]
                    val amount = items[name] ?: return@forEach
                    val missingAmount = amount - it[Items.amount]
                    if(missingAmount > 0) {
                        missing[name] = missingAmount
                    }
                }
            }

            return missing
        }

        fun launch(condition: String, amount: Long) {
            val core = Vars.state.teams.get(Team.sharded).core() ?: return

            for(i in Globals.itemList()) {
                val coreAmount = core.items.get(i).toLong()
                if(Interpreter.run(condition, mapOf("itemName" to i.name, "itemAmount" to coreAmount)) as Boolean) {
                    val toSend = java.lang.Long.min(amount, coreAmount)
                    inc(i, toSend)
                    core.items.remove(i, toSend.toInt())
                }
            }
        }

        fun value(item: Item): Long {
            return transaction {
                ensure(item)
                Items.select { Items.name eq item.name }.first()[Items.amount]
            }
        }

        fun dec(item: Item, delta: Long) {
            inc(item, -delta)
        }

        fun inc(item: Item, delta: Long) {
            transaction {
                ensure(item)
                Items.update({ Items.name eq item.name }) {
                    with(SqlExpressionBuilder) {
                        it.update(amount, amount + delta)
                    }
                }
            }
        }

        fun format(): String {
            val sb = java.lang.StringBuilder()

            transaction {
                Items.selectAll().forEach {
                    sb
                        .append(Globals.itemIcons[it[Items.name]]!!)
                        .append(it[Items.amount])
                        .append("\n")
                }
            }

            return sb.toString()
        }

        fun ensure(item: Item) {
            transaction {
                if(Items.select { Items.name eq item.name }.empty()){
                    Items.insert {
                        it[name] = item.name
                    }
                }
            }
        }
    }
}
