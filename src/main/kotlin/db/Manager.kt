package db

import arc.struct.Seq
import arc.util.Time
import kotlinx.coroutines.runBlocking
import mindustry.gen.Player
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

open class Manager<T: Manager.SetGet>(private val sg: T) {
    operator fun get(index: Long): T {
        sg.id = index
        return sg
    }

    // returns whether user with this id exists
    fun exists(id: Long): Boolean {
        sg.id = id
        return transaction { sg.table.select(sg.query).count() > 0 }
    }

    class UserManager(val ranks: Ranks, val outlook: Outlook, val testing: Boolean): Manager<UserManager.SetGet>(SetGet(ranks)) {
        // newUser creates user with set and done name, ip address and uuid can be changed,
        // newly created user is also passed to lookout to localize him and find out a best bundle
        fun new(player: Player): Driver.RawUser {
            return transaction {
                val time = Time.millis().toString()
                val id = Driver.Users.insertAndGetId {
                    it[uuid] = if(testing) time else player.uuid()
                    it[ip] = player.con.address
                    it[name] = player.name
                    it[bornDate] = Time.millis()
                }.value

                Driver.Progress.insert {
                    it[owner] = id
                }

                runBlocking {
                    outlook.input.send(Outlook.Request(player.con.address, id))
                }

                val u = load(id)
                if (testing) u.uuid = time
                u
            }
        }

        // loadUser loads a user by id
        fun load(id: Long): Driver.RawUser {
            return transaction {
                Driver.RawUser(ranks, Driver.Users.slice(Driver.Users.id, Driver.Users.name, Driver.Users.rank, Driver.Users.locale).select { Driver.Users.id eq id }.first())
            }
        }

        fun search(player: Player): Seq<Driver.RawUser> {
            val res = search(Driver.Users.uuid.eq(player.uuid()) and Driver.Users.ip.eq(player.con.address))
            if(res.size == 0) {
                return search(Driver.Users.uuid.eq(player.uuid()) or Driver.Users.ip.eq(player.con.address))
            }
            return res
        }

        // searchUser
        private fun search(op: Op<Boolean>): Seq<Driver.RawUser> {
            return transaction {
                val values = Seq<Driver.RawUser>()

                Driver.Users.slice(Driver.Users.id, Driver.Users.name, Driver.Users.rank, Driver.Users.locale).select {
                    op
                }.forEach{
                    values.add(Driver.RawUser(ranks, it))
                }

                values
            }
        }

        fun save(data: Driver.RawUser) {
            data.stats.save(data.id)
        }


        class SetGet(val ranks: Ranks): Manager.SetGet(Driver.Users) {
            var rank: Ranks.Rank
                get() {
                    return ranks[transaction {
                        Driver.Users.slice(Driver.Users.rank).select(query).first()[Driver.Users.rank]
                    }] ?: run {
                        rank = ranks.default
                        ranks.default
                    }
                }
                set(value) {
                    transaction {
                        Driver.Users.update({query}) {
                            when (value.kind) {
                                Ranks.Kind.Normal -> it[rank] = value.name
                                Ranks.Kind.Premium -> it[premium] = value.name
                                else -> {}
                            }
                        }
                    }
                }

            var password: String
                get() = get(Driver.Users.password)
                set(value) = set(Driver.Users.password, value)

            var discord: String
                get() = get(Driver.Users.discord)
                set(value) = set(Driver.Users.discord, value)

            var name: String
                get() = get(Driver.Users.name)
                set(value) = set(Driver.Users.name, value)

            var bornDate: Long
                get() = get(Driver.Users.bornDate)
                set(value) = set(Driver.Users.bornDate, value)
        }
    }

    open class SetGet(val table: LongIdTable) {
        var id = 0L

        protected fun <T> get(col: Column<T>): T {
            return transaction { table.slice(col).select(query).first()[col] }
        }

        protected fun <T> set(col: Column<T>, value: T) {
            transaction { table.update({query}){it[col] = value} }
        }

        val query get() = Op.build {table.id eq id}
    }
}