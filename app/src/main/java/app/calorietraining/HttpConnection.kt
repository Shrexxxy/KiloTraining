package app.calorietraining

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.EmptyCoroutineContext

internal fun <T> URL.withHttpConnection(read: (HttpURLConnection) -> T): T {
    val connection = openConnection() as HttpURLConnection
    try {
        return read(connection)
    } finally {
        connection.disconnect()
    }
}

internal suspend fun <T> URL.withCancellableHttpConnection(read: (HttpURLConnection) -> T): T =
    suspendCancellableCoroutine { continuation ->
        val io = Dispatchers.IO
        // Отключение сокета может блокироваться, поэтому отмена не ждёт освобождения соединения.
        io.dispatch(EmptyCoroutineContext) {
            if (!continuation.isActive) return@dispatch
            continuation.resumeWith(runCatching {
                withHttpConnection { connection ->
                    continuation.invokeOnCancellation {
                        io.dispatch(EmptyCoroutineContext) { runCatching { connection.disconnect() } }
                    }
                    if (!continuation.isActive) throw CancellationException()
                    read(connection)
                }
            })
        }
    }
