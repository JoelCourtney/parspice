package parspiceBench.workers

import parspice.sender.IntArraySender
import parspiceBench.BenchWorker

const val LENGTH = 300

class LargeOutputWorker: BenchWorker<IntArray>(IntArraySender(LENGTH)) {
    override val bytes: Int
        get() = LENGTH*Int.SIZE_BYTES
    override val iterations
        get() = mapOf(
        2 to intArrayOf(1000, 100000),
        4 to intArrayOf(1000, 10000, 100000, 1000000),
        6 to intArrayOf(100, 1000, 100000, 1000000),
        8 to intArrayOf(100, 1000, 100000, 1000000)
    )

    override fun task(i: Int): IntArray {
        val result = IntArray(LENGTH)
        for (j in 0 until LENGTH) {
            result[j] = i + j
        }
        return result
    }
}