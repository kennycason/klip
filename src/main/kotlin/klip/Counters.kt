package klip

import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

object Counters {
    private val requests = AtomicInteger(0)
    private val cacheHits = AtomicInteger(0)
    private val canvasRequests = AtomicInteger(0)

    fun incrementRequests() {
        requests.incrementAndGet()
    }

    fun incrementCacheHits() {
        cacheHits.incrementAndGet()
    }

    fun incrementCanvasRequests() {
        canvasRequests.incrementAndGet()
    }

    fun getRequests(): Int = requests.get()
    fun getCacheHits(): Int = cacheHits.get()
    fun getCanvasRequests(): Int = canvasRequests.get()

    fun getCacheHitRate(): Float {
        val requests = requests.get()
        val hits = cacheHits.get()
        return if (requests == 0) 0.0f else (hits.toFloat() / requests)
    }

    fun reset() {
        requests.set(0)
        cacheHits.set(0)
        canvasRequests.set(0)
    }
}
