package de.davis.passwordmanager.backup

sealed class Result {
    open class Success(@field:Type val type: Int) : Result()
    class Error(val message: String) : Result()
    class Duplicate(val count: Int) : Result()
}