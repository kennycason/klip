package klip

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import org.apache.logging.log4j.kotlin.Logging
import java.time.Duration
import java.time.Instant

/**
 * Pool for managing concurrent GraphicsMagick operations.
 * Uses a semaphore to limit concurrent processes based on available processors.
 */
object GraphicsMagickPool : Logging {
    private lateinit var config: Env.GraphicsMagick
    private lateinit var semaphore: Semaphore
    private var totalProcessed = 0
    private var totalWaitTime = Duration.ZERO

    fun initialize(config: Env.GraphicsMagick) {
        this.config = config
        this.semaphore = Semaphore(config.poolSize)
        logger.info { "Initialized GraphicsMagick pool with size ${config.poolSize}" }
    }

    /**
     * Executes a GraphicsMagick operation within the pooled environment.
     * Tracks metrics and ensures controlled concurrent access.
     */
    suspend fun <T> withGraphicsMagick(block: suspend () -> T): T {
        val startWait = Instant.now()
        logger.debug { "Waiting for GraphicsMagick permit. Available: ${semaphore.availablePermits}" }

        return semaphore.withPermit {
            val waitTime = Duration.between(startWait, Instant.now())
            totalWaitTime = totalWaitTime.plus(waitTime)
            totalProcessed++

//            logger.debug {
//                "Acquired GraphicsMagick permit. " +
//                    "Wait time: ${waitTime.toMillis()}ms, " +
//                    "Avg wait: ${getAverageWaitTime()}ms"
//            }

            try {
                block()
            } catch (e: Exception) {
                logger.error { "Error in GraphicsMagick operation: ${e.message}" }
                throw e
            }
        }
    }

    /**
     * Returns current pool statistics
     */
    fun getStats(): PoolStats {
        return PoolStats(
            maxConcurrent = Runtime.getRuntime().availableProcessors(),
            currentAvailable = semaphore.availablePermits,
            totalProcessed = totalProcessed,
            averageWaitMs = getAverageWaitTime()
        )
    }

    private fun getAverageWaitTime(): Long {
        if (totalProcessed == 0) return 0
        return totalWaitTime.toMillis() / totalProcessed
    }
}


/**
 * Statistics for monitoring pool performance
 */
@Serializable
data class PoolStats(
    val maxConcurrent: Int,
    val currentAvailable: Int,
    val totalProcessed: Int,
    val averageWaitMs: Long
)
