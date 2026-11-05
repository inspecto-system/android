package dev.inspecto.android.testing

import org.junit.jupiter.api.Assertions.fail

/** Runs a suspending block and returns the exception of type [T] it throws. */
suspend inline fun <reified T : Throwable> assertFailsWith(block: suspend () -> Unit): T {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) {
            return e
        }
        throw e
    }
    fail<Unit>("Expected ${T::class.java.simpleName}")
    error("unreachable")
}
