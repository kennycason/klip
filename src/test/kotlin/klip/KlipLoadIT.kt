package klip

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min
import kotlin.system.measureTimeMillis

/**
 * https://klip.arrived.com/img/properties/702/c0259e4ef8757ace42558e9aa6fcdf15.jpg?quality=90&w=956&h=468&fit=contain
 */
fun main() = runBlocking {
    val isLocal = false
    val baseUrl = if (isLocal) "http://localhost:8080/img" else "https://klip.arrived.com/img"
    val queryParams = listOf(
        "?quality=90&d=956x468",
        "?quality=90&w=512",
        "?flipV=true&rotate=90",
        "?w=128&h=128&&fit=cover",
        "?quality=75",
        "?crop&w=256&h=256&fit=contain"
    )
    val concurrencyLevel = 50     // Number of concurrent requests
    val totalRequests = 1000      // Total requests to send
    val timeoutMillis = 5000      // Timeout for each request in ms
    val maxRetries = 1           // Retry failed requests

    // metrics
    var successCount = 0
    var failureCount = 0
    val requestTimes = mutableListOf<Long>()
    val minTime = AtomicLong(Long.MAX_VALUE)
    val maxTime = AtomicLong(0)
    val totalTime = AtomicLong(0)
    val errors = mutableListOf<String>()

    val client = HttpClient(CIO) {
        engine {
            requestTimeout = timeoutMillis.toLong()
        }
    }

    // coroutine scope for concurrency
    coroutineScope {
        val semaphore = Semaphore(concurrencyLevel)
        val jobs = List(totalRequests) { index ->
            launch {
                semaphore.acquire()
                try {
                    val path = imagePaths[index % imagePaths.size]
                    val params = queryParams[index % queryParams.size]
                    val url = "$baseUrl/$path$params"
                    println("Fetching $url")

                    repeat(maxRetries) { attempt ->
                        try {
                            val elapsedTime = measureTimeMillis {
                                val response = client.get(url) {
                                    timeout {
                                        requestTimeoutMillis = timeoutMillis.toLong()
                                    }
                                }
                                if (response.status == HttpStatusCode.OK) {
                                    successCount++
                                    return@measureTimeMillis // Exit early and capture the time correctly
                                } else {
                                    failureCount++
                                    val errorBody = response.bodyAsText()
                                    errors.add("Error ${response.status} - $url - Body: $errorBody")
                                }
                            }

                            requestTimes.add(elapsedTime)
                            totalTime.addAndGet(elapsedTime)
                            minTime.getAndUpdate { min(it, elapsedTime) }
                            maxTime.getAndUpdate { max(it, elapsedTime) }
                        } catch (e: Exception) {
                            errors.add("Exception on attempt $attempt: ${e.message} - $url")
                            if (attempt == maxRetries - 1) {
                                failureCount++
                            }
                        }
                    }
                } finally {
                    semaphore.release()
                }
            }
        }
        jobs.joinAll()
    }
    client.close()

    // print Results
    println("Test Results:")
    println("Total Requests: $totalRequests")
    println("Concurrency: $concurrencyLevel")
    println("Successes: $successCount")
    println("Failures: $failureCount")
    println("Avg Response Time: ${(totalTime.get() / totalRequests.toFloat()).toLong()} ms")
    println("Min Response Time: ${minTime.get()} ms")
    println("Max Response Time: ${maxTime.get()} ms")

    // print Histogram (100ms buckets)
    val histogram = requestTimes.groupingBy { it / 100 * 100 }.eachCount().toSortedMap()
    println("Histogram (ms buckets): # requests")
    histogram.forEach { (bucket, count) ->
        println("$bucket-${bucket + 99} ms: $count")
    }

    // Print first 10 errors
    println("Errors (${errors.size} total):")
    errors.take(10).forEach { println(it) }
}

val imagePaths = listOf(
    "properties/702/c0259e4ef8757ace42558e9aa6fcdf15.jpg",
    "properties/702/267d15c0e937693a3713dc7efd02e20d.jpg",
    "properties/704/06b7b5e3bf9d92b0929beafd9d907a1a.jpg",
    "properties/692/7301e293e196f54d7bb912bb648778e2.jpg",
    "properties/707/37863f90da15b12d9a849c8f0826a567.jpg",
    "properties/697/84e55f6c6c5bde47608a321917ab92a0.jpg",
    "properties/561/bf86a962ad0a47fe0ca416bff7988f0b.jpg",
    "properties/561/270b1d5c0e583ee262c23ec658202ad2.jpg",
    "properties/561/212db23071f592fa11f590dca019dedc.jpg",
    "properties/559/cdc1bdf08221b180c6ba9abafc94b14e.jpg",
    "properties/559/33c7e6c46f0767272e9b977c708c0230.jpg",

    "properties/100/072710d5196dd48dfef632e4c32add50.jpg",
    "properties/100/1115bcb5bdc5f5a4c00b6fb70b468cd5.jpg",
    "properties/100/15c34cf21dae0cb27a70f71c76d82565.jpg",
    "properties/100/1f1bdc3ec9cfd8cf8e19c93cf3ec56f0.jpg",
    "properties/100/2358dc1c2d2a00f1707e083759e49e0d.jpg",
    "properties/100/2895165308fdecc12e337ecf519c10d3.jpg",
    "properties/100/2daf3acc8e4534902194e33a447ccea1.jpg",
    "properties/100/2f1e9942fd265554156539b32b762e1d.jpg",
    "properties/100/39c4b82e3bbf8bca0609ec15b0b382ce.jpg",
    "properties/100/3b4581bcf58cce846e2b276e2bc4c4d2.jpg",
    "properties/100/41af70ab95511461d6ae5b211ace747a.jpg",
    "properties/100/44fb642d0dd203897c851e03f9d9e2b3.jpg",
    "properties/100/515454dcb027e0fcc6e34c1ca296e0c7.jpg",
    "properties/100/565eb2dfb07ff6a1c07b25c59d0f5137.jpg",
    "properties/100/5a0ddaf3676cc08825f69f8ad49a5c1e.jpg",
    "properties/100/5ffdd8d24d2a0d9f9942cec09dec817f.jpg",
    "properties/100/6b4d40630bdcef2ea4c37e328eeacfc4.jpg",
    "properties/100/73f19a0fe563456a9af19e2915606824.jpg",
    "properties/100/846da00313519eb4be2a4677ab40ec28.jpg",
    "properties/100/8856ecdf74d52996cc2626721d8ce8d4.jpg",
    "properties/100/90db00ac7ac5fd17c8aaa2643690b06e.jpg",
    "properties/100/92b0c9f5f8de89884df80b865b6b0cfb.jpg",
    "properties/100/941977b136f6b61a33c7da4e20b4b489.jpg",
    "properties/100/9ef42459f98e56ba284ee797b10d548d.jpg",
    "properties/100/a8738fc4803f897965b8caaada2d9cbd.jpg",
    "properties/100/aaf344aee135693e94129d799d63e135.jpg",
    "properties/100/ab8149fccd4c8867ffd728f4c020a8e6.jpg",
    "properties/100/b1ff3276dd89743d2cd6eaeb220fe9b0.jpg",
    "properties/100/b48d90d3e15fc9809dc61f5e09cadb4d.jpg",
    "properties/100/c4e42b7e025b3ead45a7aa3510c1b5c7.jpg",
    "properties/100/d5a308ae26080387206c7fad506565cf.jpg",
    "properties/100/dfc45da59cdd2be39b758774ab2a4ddc.jpg",
    "properties/100/e10949203acb6e2123ef08528c8780a8.jpg",
    "properties/100/e12c9c0561d1919092b6909e2be24c4e.jpg",
    "properties/100/e8fe3531ba87a4faf539f4a6dff73d9a.jpg",
    "properties/100/ec5cc3e777b692bb57966bb409ea57bc.jpg",
    "properties/100/f0479c1c1ac83b95b64a029359300b46.jpg",
    "properties/100/f06ac3b26ad05064a7d2dee26b4d67e6.jpg",

    "properties/300/00f2272e097c4a723318e2211aa46163.jpg",
    "properties/300/06245c628fdcc4fc1f10ec883aa60031.jpg",
    "properties/300/06aba1b8c2ea17f4d76771ce1b5315c9.jpg",
    "properties/300/08aca7361d84e51435945b606d4cef3c.jpg",
    "properties/300/092a196181d5dbd92a4978efe05d5a7e.jpg",
    "properties/300/0adb7ea5f99b7ec6ab5a96d8c04c371c.jpg",
    "properties/300/0d7461cf38b364b2a95a3d0e6664aa98.jpg",
    "properties/300/0e14096d77e1c0c53c3f96da96d02ccd.jpg",
    "properties/300/0e187d42f41ca5ec4154f70e70500c2f.png",
    "properties/300/103650546a72067e82a125fef35e6dbe.png",
    "properties/300/111f5ecb4e1adedc594197de924f5c92.jpg",
    "properties/300/156339c7bfe613bf9917573df1bf7f8d.jpg",
    "properties/300/156969cc93ec7639958b48345d250a40.png",
    "properties/300/175b9e9174c55f1e6d4cd42ec6e68390.jpg",
    "properties/300/18cb4a35d0a986de385cd2759c293170.jpg",
    "properties/300/194aa751bb3dfabf18e80e55e09e1107.png",
    "properties/300/1c5aae3444cb31f53f66e83095a64966.jpg",
    "properties/300/1fa8aedf404b5eb2561c111c0adb3f77.jpg",
    "properties/300/205b46b17ac86f99a4ef3ea8182bcb37.jpg",
    "properties/300/21a2393e12360447494030f7e5356dac.jpg",
    "properties/300/2232a7bd92b67582dc4f9ef71f360680.png",
    "properties/300/266b5a32d77197b08d7faca15857879a.png",
    "properties/300/29190f6ff56458d6f9872adf966935bd.jpg",
    "properties/300/2a01f5bf89c90cfb98e479165d8b4e1e.jpg",
    "properties/300/2b9670e6dfd612391fcb2192eab1e6cb.jpg",
    "properties/300/2d44077cc8dfddcde6fad0ddbeb43637.jpg",
    "properties/300/2e6d110a095c1e6dc4cd152dfcc8ae12.jpg",
    "properties/300/2ecf4b5924d53023319427c84c44bc6e.jpg",
    "properties/300/2f29432359aef827ba9497f62ac5b2fd.png",
    "properties/300/346ad378525bd21615422d559c17d018.jpg",
    "properties/300/3772178a69b3443dc9d1cb9588c313a6.jpg",
    "properties/300/388a5ee7d352a7a930d3da9736cdb1aa.jpg",
    "properties/300/3bbdaab8c6414582e50e6acd540cb2b0.jpg",
    "properties/300/40f24b7e4f7cbddfb6b2513f241a24c5.jpg",
    "properties/300/455379e8eb9150c60ad7bf1b113bbad7.png",
    "properties/300/49877baef624f62543bd1997461d05af.jpg",
    "properties/300/4b1bdd73be1c0ceb8b9d71933ad6ab8b.jpg",
    "properties/300/4c5418c52e99298d0342674034d8e141.jpg",
    "properties/300/4cef42766da36e58b871a0b3ece515f6.jpg",
    "properties/300/527c38c276bf21ff5c1c95da4287d89d.png",
    "properties/300/54c1176d4b2c687321b763736dabc754.png",
    "properties/300/595c273a4b1b472f6d2c87ea7abbe79f.jpg",
    "properties/300/59ee41b64f8899285ebda628ee042c14.jpg",
    "properties/300/5b023bc2995a481cbcb09ac02193a2c3.png",
    "properties/300/5bd8f1e7941d95db4216858d05d4f1cb.jpg",
    "properties/300/629234eb0cf8232907dc8e99f8d2b3dd.jpg",
    "properties/300/6501bab3283fa85e6be0b005167d3af6.png",
    "properties/300/65a2d2b2048c0bf3eaa7b5dcb24760fb.png",
    "properties/300/6766a4ceb31520370ea8a7d3a966f9f5.png",
    "properties/300/6e2fe8c8f5ebba6c37c1b774a9a834e6.jpg",
    "properties/300/6fcefacd6a89ed9771f4527b9e7e0659.png",
    "properties/300/78507de57bac83edfaf5a1bee6a7fff7.jpg",
    "properties/300/7934a47e72d411c112af2fef656f3383.jpg",
    "properties/300/7b73c07122799d3afc13c5e96d1d5b1c.png",
    "properties/300/7d2eb0264027c4385f54e4ac698dcb1f.png",
    "properties/300/7feb2c5618aaf7fad58379d640565cb0.jpg",
    "properties/300/83d2acff56d9fb877feca62bff4e4371.jpg",
    "properties/300/850ebecd5e81685bd88c9841a635cbed.jpg",
    "properties/300/85a46bf5281a54d9542b1438f56acca7.png",
    "properties/300/87ddacae65ae495d9e4f1255dd75d0dd.jpg",
    "properties/300/882d285f217e451c3e1ef1b86cf27bae.jpg",
    "properties/300/8dcd497bbf71f14ae048d4fe305fb936.jpg",
    "properties/300/92f487428ffb7ed401d77cae62bc1efc.png",
    "properties/300/9464aca96cf7506ded4c6c80eda6a627.jpg",
    "properties/300/955d5dc0949e624dcfc6b3ac054ab92f.jpg",
    "properties/300/96b69ed2abf00ad1ac54e08159fc785e.jpg",
    "properties/300/9cca6a0f1eb939223c64060ad5c5a72d.jpg",
    "properties/300/9ed8fca9882c9f68ec13c2dc8b1517ed.jpg",
    "properties/300/a1bafcce928a87834ac8b8b5bf1b85ec.png",
    "properties/300/a43b82dddd9f97997d8589aab95bccb8.jpg",
)
    .shuffled()

/*
Total Requests: 1000
Concurrency: 100
Successes: 3000
Failures: 0
Avg Response Time: 1433 ms
Min Response Time: 120 ms
Max Response Time: 1917 ms
Histogram (ms buckets): # requests
100-199 ms: 60
200-299 ms: 535
300-399 ms: 941
400-499 ms: 509
500-599 ms: 329
600-699 ms: 268
700-799 ms: 145
800-899 ms: 69
900-999 ms: 26
1000-1099 ms: 11
1100-1199 ms: 4
1200-1299 ms: 4
1300-1399 ms: 7
1400-1499 ms: 9
1500-1599 ms: 20
1600-1699 ms: 10
1700-1799 ms: 33
1800-1899 ms: 18
1900-1999 ms: 2
 */

/*
Test Results:
Total Requests: 2500
Concurrency: 250
Successes: 7500
Failures: 0
Avg Response Time: 3311 ms
Min Response Time: 179 ms
Max Response Time: 2229 ms
Histogram (ms buckets): # requests
100-199 ms: 5
200-299 ms: 15
300-399 ms: 28
400-499 ms: 27
500-599 ms: 47
600-699 ms: 76
700-799 ms: 158
800-899 ms: 727
900-999 ms: 1438
1000-1099 ms: 1651
1100-1199 ms: 1179
1200-1299 ms: 803
1300-1399 ms: 590
1400-1499 ms: 360
1500-1599 ms: 179
1600-1699 ms: 76
1700-1799 ms: 40
1800-1899 ms: 39
1900-1999 ms: 28
2000-2099 ms: 21
2100-2199 ms: 12
2200-2299 ms: 1
 */

/*
Test Results:
Total Requests: 5000
Concurrency: 400
Successes: 15000
Failures: 0
Avg Response Time: 4579 ms
Min Response Time: 286 ms
Max Response Time: 3559 ms
Histogram (ms buckets): # requests
200-299 ms: 1
300-399 ms: 12
400-499 ms: 44
500-599 ms: 55
600-699 ms: 70
700-799 ms: 60
800-899 ms: 48
900-999 ms: 79
1000-1099 ms: 93
1100-1199 ms: 85
1200-1299 ms: 658
1300-1399 ms: 2706
1400-1499 ms: 3507
1500-1599 ms: 3005
1600-1699 ms: 2000
1700-1799 ms: 1083
1800-1899 ms: 605
1900-1999 ms: 346
2000-2099 ms: 221
2100-2199 ms: 80
2200-2299 ms: 54
2300-2399 ms: 26
2400-2499 ms: 34
2500-2599 ms: 36
2600-2699 ms: 33
2700-2799 ms: 25
2800-2899 ms: 13
2900-2999 ms: 15
3000-3099 ms: 4
3300-3399 ms: 1
3500-3599 ms: 1
 */
