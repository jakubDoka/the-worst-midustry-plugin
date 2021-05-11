package db

import bundle.Bundle
import arc.struct.Seq
import com.beust.klaxon.Klaxon
import kotlinx.coroutines.runBlocking
import mindustry.gen.Player
import mindustry_plugin_utils.Messenger
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

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
            if(testing) SchemaUtils.drop(User, Bans)
            SchemaUtils.create(User, Bans)
        }

    }

    private fun initMessenger(verbose: Boolean) {
        messenger = Messenger("DatabaseDriver", "enable verbose by adding '\"verbose\": true' to config", verbose)
    }

    // markGriefer changes status of player to griefer but nothing else
    fun markGriefer(id: Long) {
        transaction {
            User.update({ User.id eq id }) {
                it[rank] = Ranks.griefer
            }
        }
    }

    // newUser creates user with set and done name, ip address and uuid can be changed,
    // newly created user is also passed to lookout to localize him and find out a best bundle
    fun newUser(player: Player): RawUser {
        return transaction {
            val id = User.insertAndGetId {
                it[uuid] = player.uuid()
                it[ip] = player.con.address
                it[name] = player.name
            }.value

            runBlocking {
                outlook.input.send(Outlook.Request(player.con.address, id))
            }

            loadUser(id)
        }
    }

    // loadUser loads a user by id
    fun loadUser(id: Long): RawUser {
        return transaction {
            RawUser(ranks, User.slice(User.id, User.name, User.rank).select { User.id eq id }.first())
        }
    }

    fun saveUser(user: RawUser) {

    }

    // searchUser
    fun searchUsers(player: Player): Seq<RawUser> {
        return transaction {
            val values = Seq<RawUser>()

            User.slice(User.id, User.name, User.rank).select {
                User.uuid.eq(player.uuid()).or(User.ip.eq(player.con.address)).and(User.password.neq(User.noPassword))
            }.forEach{
                values.add(RawUser(ranks, it))
            }

            values
        }
    }

    // Raw USer stores data about user retrieved from database. This data is critical and retrieving
    // it from database each time would affect performance. Data has to be saved when player disconnects
    class RawUser(ranks: Ranks, row: ResultRow? = null) {
        var name = "unknown"
        var id: Long = -1
        var rank = Ranks.paralyzed
        var bundle = Bundle()

        init {
            if (row != null) {
                name = row[User.name]
                id = row[User.id].value
                rank = ranks[row[User.rank]] ?: Ranks.default
                bundle = Bundle(row[User.locale])
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

    // User is definition of user table (Exposed framework macro)
    object User : LongIdTable() {
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
