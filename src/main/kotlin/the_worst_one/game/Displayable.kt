package the_worst_one.game

import the_worst_one.db.Driver

interface Displayable {
    fun tick()
    fun display(user: Driver.RawUser): String
}