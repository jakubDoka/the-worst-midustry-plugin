package db

import arc.struct.Seq
import arc.util.Time
import bundle.Bundle
import cfg.Globals
import cfg.Reloadable
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import db.Driver.Progress.default
import db.Driver.Users.default
import db.Driver.Users.index
import game.commands.Configure
import kotlinx.coroutines.runBlocking
import mindustry.gen.Player
import mindustry_plugin_utils.Messenger
import mindustry_plugin_utils.Fs
import mindustry_plugin_utils.Templates.time
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.sql.ResultSet
import java.util.*
import kotlin.collections.HashSet

// Driver handles all database calls and holds information about db structure
class Driver(override val configPath: String = "config/driver.json", val ranks: Ranks = Ranks()): Reloadable {
    lateinit var config: Config
    private lateinit var messenger: Messenger
    private lateinit var con: Database
    private val outlook = Outlook()
    val users = UserManager(ranks, outlook, this)

    // loads the config and opens db connection
    init {
        reload()
    }

    override fun reload() {
        if(this::con.isInitialized) {
            TransactionManager.current().close()
        }

        try {
            config = Klaxon().parse<Config>(File(configPath))!!
            initMessenger(config.verbose)
        } catch (e: Exception) {
            initMessenger(false)
            messenger.log("failed to load config")
            messenger.verbose { e.printStackTrace() }
            config = Config()
            Fs.createDefault(configPath, config)
        }

        messenger.log("Connecting to database...")
        val url = String.format("jdbc:postgresql:%s", config.database)
        con = Database.connect(url, user = config.user, password = config.password)
        messenger.log("Connected.")

        drop()
    }

    fun drop() {
        transaction {
            if(Globals.testing) SchemaUtils.drop(Users, Bans, Progress)
            SchemaUtils.create(Users, Bans, Progress)
            transaction {
                exec("create index if not exists points on Users (points desc)")
            }
        }
    }

    private fun initMessenger(verbose: Boolean) {
        messenger = Messenger("DatabaseDriver", "enable verbose by adding '\"verbose\": true' to config", verbose)
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



    // banned returns whether player is banned on this server
    fun banned(player: Player): Boolean {
        return transaction { banned(player.uuid()) || banned(player.con.address.subnet()) }
    }

    // ban bans the player
    fun ban(player: Player) {
        transaction {
            ban(player.uuid())
            ban(player.con.address.subnet())
        }
    }

    // ban adds ip or uuid to death note
    fun ban(value: String) {
        transaction { if(!banned(value)) Bans.insert { it[Bans.value] = value } }
    }

    // banned returns whether siring is in death note
    fun banned(value: String): Boolean {
        return transaction { !Bans.select {Bans.value eq value}.empty() }
    }

    // Subnet returns subnet part of ip address
    fun String.subnet(): String {
        if (!contains(".")) return ""
        return substring(0, lastIndexOf('.'))
    }

    // login logs player into different account
    fun login(id: Long, player: Player) {
        transaction {
            Users.update({ Users.id eq id }){
                it[name] = player.name
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

    class UserManager(val ranks: Ranks, val outlook: Outlook, val driver: Driver) {
        fun exists(id: Long): Boolean {
            return transaction { !Users.select{Users.id eq id}.empty() }
        }

        // newUser creates user with set and done name, ip address and uuid can be changed,
        // newly created user is also passed to lookout to localize him and find out a best bundle
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
                    outlook.input.send(Outlook.Request(player.con.address, id))
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
        var discord = row?.get(Users.discord) ?: Users.noDiscord
        var password = row?.get(Users.password) ?: Users.noPassword

        var rank = ranks[row?.get(Users.rank)] ?: ranks.paralyzed
        var display = ranks[row?.get(Users.display)] ?: rank
        val specials = HashSet(row?.get(Users.specials)?.split(" ") ?: listOf())
        val stats = Stats(id)
        val age = Time.millis() - (row?.get(Users.bornDate) ?: 0)

        val country = row?.get(Users.country) ?: "unknown"
        val bundle = Bundle(row?.get(Users.locale) ?: "en_US")

        init {
            if(!specials.contains(display.name)) {
                display = rank
            }
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

        fun save(multiplier: Stats, ranks: Ranks) {
            stats.save(id)
            transaction {
                Users.update({ Users.id eq this@RawUser.id }) {
                    it[specials] = this@RawUser.specials.joinTo(StringBuilder(), " ").toString()
                    it[name] = this@RawUser.name
                    it[discord] = this@RawUser.discord
                    it[password] = this@RawUser.password
                    it[rank] = this@RawUser.rank.name
                    it[display] = this@RawUser.display.name
                    it[points] = points(ranks, multiplier)
                }
            }
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

        fun isSpectator(): Boolean {
            return rank.control == Ranks.Control.None
        }
    }

    // Config holds database config
    class Config(
        var user: String = "postgres",
        var password: String = "twstest123",
        var database: String = "mtest",
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

        val password = text("password").default(noPassword)
        val rank = text("rank").default(defaultRank)
        val specials = text("specials").default("")
        val premium = text("premium").default(noPremium)
        val display = text("display").default("")

        val country = text("country").default(defaultCountry)
        val locale = text("locale").default(defaultLocale)

        val points = long("points").index().default(0)
    }

    // Bas is definition of bans table (Exposed framework macro)
    object Bans: Table() {
        val value  = text("value").uniqueIndex()
        override val primaryKey = PrimaryKey(value)
    }

    // Progress stored all common stats obtained by playing game casually
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
                            playTime / (multiplier.playTime + 1)
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
                }
            }
        }

        fun onMessage() {
            lastMessage = Time.millis()
        }
    }
}
