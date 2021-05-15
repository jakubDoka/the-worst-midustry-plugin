package db

import bundle.Bundle
import com.beust.klaxon.Klaxon
import db.Driver.Progress.default
import db.Driver.Users.default
import db.Driver.Users.index
import game.commands.Configure
import mindustry.gen.Player
import mindustry_plugin_utils.Messenger
import mindustry_plugin_utils.Fs
import mindustry_plugin_utils.Templates.time
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.sql.ResultSet

// Driver handles all database calls and holds information about db structure
class Driver(override val configPath: String = "config/driver.json", val ranks: Ranks = Ranks(), val testing: Boolean = false): Configure.Reloadable {
    lateinit var config: Config
    private lateinit var messenger: Messenger
    private lateinit var con: Database
    private val outlook = Outlook()
    val users = Manager.UserManager(ranks, outlook, testing)

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

        val url = String.format("jdbc:postgresql:%s", config.database)
        con = Database.connect(url, user = config.user, password = config.password)

        drop()
    }

    fun drop() {
        transaction {
            if(testing) SchemaUtils.drop(Users, Bans, Progress)
            SchemaUtils.create(Users, Bans, Progress)
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

    // Raw USer stores data about user retrieved from database. This data is critical and retrieving
    // it from database each time would affect performance. Data has to be saved when player disconnects
    class RawUser(ranks: Ranks, row: ResultRow? = null) {
        var name = "unknown"
        var id: Long = -1
        var rank = Ranks.paralyzed
        var bundle = Bundle()
        var uuid: String = ""
        var stats = Stats()

        init {
            if (row != null) {
                name = row[Users.name]
                id = row[Users.id].value
                rank = ranks[row[Users.rank]] ?: ranks.default
                bundle = Bundle(row[Users.locale])
                stats = Stats(id)
            }
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
    fun logout(data: RawUser) {
        transaction {
            if(users[data.id].password == Users.noPassword) {
                Users.deleteWhere { Users.id eq data.id }
            } else {
                Users.update({ Users.id eq data.id }){
                    it[ip] = "logged.off"
                }
            }

        }
    }

    // Config holds database config
    class Config(
        var user: String = "postgres",
        var password: String = "helloThere",
        var database: String = "mtest",
        var verbose: Boolean = false,
    )

    // Users is definition of user table (Exposed framework macro)
    object Users : LongIdTable() {
        val noDiscord = "none"
        val noPassword = "none"
        val defaultRank = "newcomer"
        val noPremium = "none"
        val defaultCountry = "unknown"
        val defaultLocale = "en_Us"

        val name = text("name")
        val uuid = text("uuid").index()
        val discord = text("discord").index().default(noDiscord)
        val ip = text("ip").index()
        val bornDate = long("bornDate")

        val password = text("password").default(noPassword)
        val rank = text("rank").default(defaultRank)
        val specials = text("specials").default("")
        val premium = text("premium").default(noPremium)

        val country = text("country").default(defaultCountry)
        val locale = text("locale").default(defaultLocale)
    }

    class PersonalData(val id: Long, ranks: Ranks, row: ResultRow = Users.select{Users.id eq id}.first() ) {
        val name = row[Users.name]
        val discord = row[Users.discord]
        val bornDate = row[Users.bornDate].time()

        val rank = ranks.getOrDefault(row[Users.rank], ranks.default)
        val specials = {
        }
        val premium = ranks[row[Users.premium]]

        val country = row[Users.country]
    }

    // Bas is definition of bans table (Exposed framework macro)
    object Bans: Table() {
        val value  = text("value").uniqueIndex()
        override val primaryKey = PrimaryKey(value)
    }

    // Progress stored all common stats obtained by playing game casually
    object Progress: Table() {
        val owner = long("owner")
        val build = long("built").default(0)
        val destroyed = long("destroyed").default(0)
        val killed = long("killed").default(0)
        val deaths = long("deaths").default(0)
        val played = long("played").default(0)
        val won = long("won").default(0)
        val messages = long("message").default(0)
        val commands = long("commands").default(0)
        override val primaryKey = PrimaryKey(owner)
    }

    object Votes: Table() {
        //TODO(counter for votes)
    }

    // Stats is ram representation of progress and is used to increase the counter.
    // When player disconnects, new values are saved to database
    class Stats(owner: Long = -1) {
        // i "love" ths boiler plates
        var build: Long = 0
        var destroyed: Long = 0
        var killed: Long = 0
        var deaths: Long = 0
        var played: Long = 0
        var won: Long = 0
        var messages: Long = 0
        var commands: Long = 0

        init {
            if(owner != -1L) {
                transaction {
                    val row = Progress.select { Progress.owner eq owner }.first()
                    build = row[Progress.build]
                    destroyed = row[Progress.destroyed]
                    killed = row[Progress.killed]
                    deaths = row[Progress.deaths]
                    played = row[Progress.played]
                    won = row[Progress.won]
                    messages = row[Progress.messages]
                    commands = row[Progress.commands]
                }
            }
        }

        fun save(owner: Long) {
            transaction {
                Progress.update({Progress.owner eq owner}) {
                    it[build] = build
                    it[destroyed] = destroyed
                    it[killed] = killed
                    it[deaths] = deaths
                    it[played] = played
                    it[won] = won
                    it[messages] = messages
                    it[commands] = commands
                }
            }
        }
    }
}
