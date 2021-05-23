package game

import db.Driver

interface Displayable {
    fun tick()
    fun display(user: Driver.RawUser): String
}