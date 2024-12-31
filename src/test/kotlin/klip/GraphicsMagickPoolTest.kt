package klip

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class GraphicsMagickPoolTest {
    @Test
    fun testConcurrentProcessing() = runBlocking {
        GraphicsMagickPool.initialize(Env.GraphicsMagick())

        val totalTasks = 20
        val taskDuration = 500.milliseconds

        val startTime = System.currentTimeMillis()

        coroutineScope {
            // Launch multiple concurrent image processing tasks
            val jobs = (1..totalTasks).map { taskId ->
                async {
                    GraphicsMagickPool.withGraphicsMagick {
                        println("Starting task $taskId")
                        delay(taskDuration) // Simulate processing
                        println("Completed task $taskId")
                        taskId
                    }
                }
            }
            jobs.awaitAll()
        }

        val totalTime = System.currentTimeMillis() - startTime
        val stats = GraphicsMagickPool.getStats()

        println("""
            Test completed:
            - Total tasks: $totalTasks
            - Total time: ${totalTime}ms
            - Max concurrent: ${stats.maxConcurrent}
            - Average wait: ${stats.averageWaitMs}ms
            - Total processed: ${stats.totalProcessed}
        """.trimIndent())
    }
}
