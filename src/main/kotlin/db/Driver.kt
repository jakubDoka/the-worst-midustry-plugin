package db

import bundle.Bundle
import arc.struct.Seq
import arc.util.Strings
import arc.util.Time
import com.beust.klaxon.Klaxon
import game.commands.Discord
import kotlinx.coroutines.runBlocking
import mindustry.gen.Player
import mindustry_plugin_utils.Messenger
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import util.Fs
import java.io.File
import java.io.FileNotFoundException
import java.lang.Long.parseLong
import java.lang.StringBuilder
import java.sql.ResultSet

// Driver handles all database calls and holds information about db structure
class Driver(configPath: String, val ranks: Ranks = Ranks(), val testing: Boolean = false) {
    private var config: Config
    private lateinit var messenger: Messenger
    private val con: Database
    private val outlook = Outlook()

    // loads the config and opens db connection
    init {
        try {
            config = Klaxon().parse<Config>(File(configPath))!!
            initMessenger(config.verbose)
        } catch (e: FileNotFoundException) {
            config = Config()
            Fs.createDefault(configPath, config)
        } catch (e: Exception) {
            initMessenger(false)
            messenger.log("failed to load config")
            messenger.verbose {
                e.printStackTrace()
            }
            config = Config()
        }

        val url = String.format("jdbc:postgresql:%s", config.database)
        con = Database.connect(url, user = config.user, password = config.password)

        transaction {
            if(testing) SchemaUtils.drop(Users, Bans)
            SchemaUtils.create(Users, Bans)
        }

    }

    private fun initMessenger(verbose: Boolean) {
        messenger = Messenger("DatabaseDriver", "enable verbose by adding '\"verbose\": true' to config", verbose)
    }

    fun setRank(id: Long, value: Ranks.Rank) {
        transaction {
            Users.update({ Users.id eq id }) {
                when (value.kind) {
                    Ranks.Kind.Normal -> it[rank] = value.name
                    Ranks.Kind.Premium -> it[premium] = value.name
                    else -> {}
                }
            }
        }
    }

    // get rank returns rank of give id of newcomer if rank si invalid, it also fixes rank to newcomer if that happens
    fun getRank(id: Long): Ranks.Rank {
        return ranks[transaction {
            Users.slice(Users.rank).select{Users.id eq id}.first()[Users.rank]
        }] ?: run {
            setRank(id, ranks.default)
            ranks.default
        }
    }

    // returns whether user with this id exists
    fun userExists(id: String): Boolean {
        return Strings.canParseInt(id) && transaction { Users.select {Users.id eq parseLong(id)}.count() > 0 }
    }

    // newUser creates user with set and done name, ip address and uuid can be changed,
    // newly created user is also passed to lookout to localize him and find out a best bundle
    fun newUser(player: Player): RawUser {
        return transaction {
            val time = Time.millis().toString()
            val id = Users.insertAndGetId {
                it[uuid] = if(testing) time else player.uuid()
                it[ip] = player.con.address
                it[name] = player.name
            }.value

            runBlocking {
                outlook.input.send(Outlook.Request(player.con.address, id))
            }

            val u = loadUser(id)
            if (testing) u.uuid = time
            u
        }
    }

    // loadUser loads a user by id
    fun loadUser(id: Long): RawUser {
        return transaction {
            RawUser(ranks, Users.slice(Users.id, Users.name, Users.rank, Users.locale).select { Users.id eq id }.first())
        }
    }

    // searchUser
    fun searchUsers(player: Player): Seq<RawUser> {
        return transaction {
            val values = Seq<RawUser>()
            Users.slice(Users.id, Users.name, Users.rank, Users.locale).select {
                Users.uuid.eq(player.uuid()) or Users.ip.eq(player.con.address)
            }.forEach{
                values.add(RawUser(ranks, it))
            }

            values
        }
    }

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

        init {
            if (row != null) {
                name = row[Users.name]
                id = row[Users.id].value
                rank = ranks[row[Users.rank]] ?: ranks.default
                bundle = Bundle(row[Users.locale])
            }
        }
    }

    // banned returns whether player is banned on this server
    fun banned(player: Player): Boolean {
        return transaction { banned(player.uuid()) || banned(player.con.address) }
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
        return transaction { Bans.select {Bans.value eq value}.empty() } 
    }

    // Subnet returns subnet part of ip address
    fun String.subnet(): String {
        return substring(0, lastIndexOf('.'))
    }

    // Config holds database config
    class Config(
        val user: String = "postgres",
        val password: String = "helloThere",
        val database: String = "mtest",
        val verbose: Boolean = false,
    )

    // Users is definition of user table (Exposed framework macro)
    object Users : LongIdTable() {
        val noPassword = "none"
        val defaultRank = "newcomer"
        val noPremium = "none"
        val defaultCountry = "unknown"
        val defaultLocale = "en_Us"

        val name = text("name")
        val uuid = text("uuid").uniqueIndex()
        val ip = text("ip").index()

        val password = text("password").default(noPassword)
        val rank = text("rank").default(defaultRank)
        val specials = text("specials").default("")
        val premium = text("premium").default(noPremium)

        val country = text("country").default(defaultCountry)
        val locale = text("locale").default(defaultLocale)
    }

    // Bas is definition of bans table (Exposed framework macro)
    object Bans: Table() {
        val value  = text("value").uniqueIndex()
        override val primaryKey = PrimaryKey(value)
    }
}
