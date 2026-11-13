package com.gammagamma.lax.slack.desktop

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal read-only LevelDB reader: find the newest value whose user key ends
 * with [suffix] (Chromium Local Storage `localConfig_v2`).
 */
internal class LevelDbScanner(private val suffix: String) {
    private var bestKey: ByteArray? = null
    private var bestValue: ByteArray? = null
    private var bestSeq: Long = -1
    private var bestDeletedSeq: Long = 0

    fun take(): Pair<ByteArray, ByteArray>? {
        val key = bestKey ?: return null
        val value = bestValue ?: return null
        bestKey = null
        bestValue = null
        if (bestDeletedSeq > bestSeq) return null
        return key to value
    }

    fun scanTable(file: ByteArray) {
        if (file.size < FOOTER_LEN) return
        val footer = file.copyOfRange(file.size - FOOTER_LEN, file.size)
        val magic = leLong(footer, 40)
        if (magic != TABLE_MAGIC) return
        val pos = intArrayOf(0)
        readHandle(footer, pos) // metaindex
        val indexHandle = readHandle(footer, pos)
        val indexBlock = readBlock(file, indexHandle) ?: return
        val indexIter = BlockIter(indexBlock)
        while (true) {
            val entry = indexIter.next() ?: break
            val hp = intArrayOf(0)
            val h = readHandle(entry.second, hp)
            val block = readBlock(file, h) ?: continue
            val it = BlockIter(block)
            while (true) {
                val kv = it.next() ?: break
                offerInternal(kv.first, kv.second)
            }
        }
    }

    fun scanLog(file: ByteArray) {
        val batch = ArrayList<Byte>()
        var pos = 0
        while (pos < file.size) {
            val blockLeft = LOG_BLOCK - (pos % LOG_BLOCK)
            if (blockLeft < LOG_HEADER) {
                pos += blockLeft
                continue
            }
            if (pos + LOG_HEADER > file.size) break
            val len = leShort(file, pos + 4)
            val kind = file[pos + 6].toInt() and 0xff
            if (kind == 0 && len == 0) {
                pos += blockLeft
                continue
            }
            pos += LOG_HEADER
            if (len > blockLeft - LOG_HEADER || pos + len > file.size) break
            val frag = file.copyOfRange(pos, pos + len)
            pos += len
            when (kind) {
                1 -> {
                    batch.clear()
                    scanBatch(frag)
                }
                2 -> {
                    batch.clear()
                    batch.addAll(frag.toList())
                }
                3 -> batch.addAll(frag.toList())
                4 -> {
                    batch.addAll(frag.toList())
                    scanBatch(batch.toByteArray())
                    batch.clear()
                }
                else -> return
            }
        }
    }

    private fun scanBatch(data: ByteArray) {
        if (data.size < 12) return
        var seq = leLong(data, 0)
        val count = leInt(data, 8)
        val pos = intArrayOf(12)
        repeat(count) {
            if (pos[0] >= data.size) return
            val kind = data[pos[0]++].toInt() and 0xff
            val key = take(data, pos, varint(data, pos).toInt()) ?: return
            val value = if (kind == 1) take(data, pos, varint(data, pos).toInt()) ?: return else ByteArray(0)
            offer(key, seq, kind, value)
            seq += 1
        }
    }

    private fun offerInternal(internalKey: ByteArray, value: ByteArray) {
        if (internalKey.size < 8) return
        val split = internalKey.size - 8
        val tag = leLong(internalKey, split)
        val seq = tag ushr 8
        val kind = (tag and 0xff).toInt()
        offer(internalKey.copyOf(split), seq, kind, value)
    }

    private fun offer(key: ByteArray, seq: Long, kind: Int, value: ByteArray) {
        val keyStr = key.decodeToString()
        if (!keyStr.endsWith(suffix)) return
        if (kind == 0) {
            bestDeletedSeq = maxOf(bestDeletedSeq, seq)
            return
        }
        if (kind != 1) return
        if (bestSeq >= seq) return
        bestKey = key
        bestValue = value
        bestSeq = seq
    }

    private fun readBlock(file: ByteArray, h: Handle): ByteArray? {
        if (h.offset > file.size || h.size > file.size - h.offset) return null
        val off = h.offset.toInt()
        val size = h.size.toInt()
        if (off + size + 5 > file.size) return null
        val raw = file.copyOfRange(off, off + size)
        return when (file[off + size].toInt() and 0xff) {
            0 -> raw
            1 -> runCatching { Snappy.decompress(raw) }.getOrNull()
            else -> null
        }
    }

    private class BlockIter(block: ByteArray) {
        private val data: ByteArray
        private var pos = 0
        private var key = ByteArray(0)

        init {
            require(block.size >= 4)
            val restarts = leInt(block, block.size - 4)
            val tail = 4 + 4 * restarts
            require(tail <= block.size)
            data = block.copyOf(block.size - tail)
        }

        fun next(): Pair<ByteArray, ByteArray>? {
            if (pos >= data.size) return null
            val p = intArrayOf(pos)
            val shared = varint(data, p).toInt()
            val nonShared = varint(data, p).toInt()
            val valueLen = varint(data, p).toInt()
            if (shared > key.size) return null
            val delta = take(data, p, nonShared) ?: return null
            val value = take(data, p, valueLen) ?: return null
            key = key.copyOf(shared) + delta
            pos = p[0]
            return key to value
        }
    }

    private data class Handle(val offset: Long, val size: Long)

    private fun readHandle(data: ByteArray, pos: IntArray): Handle =
        Handle(varint(data, pos), varint(data, pos))

    companion object {
        // LevelDB table magic 0xdb4775248b80fb57 as signed Long
        private val TABLE_MAGIC: Long = -2646017456237118633L
        private const val FOOTER_LEN = 48
        private const val LOG_BLOCK = 32768
        private const val LOG_HEADER = 7

        private fun varint(data: ByteArray, pos: IntArray): Long {
            var result = 0L
            var shift = 0
            while (pos[0] < data.size && shift < 64) {
                val b = data[pos[0]++].toInt() and 0xff
                result = result or ((b and 0x7f).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
            error("corrupt")
        }

        private fun take(data: ByteArray, pos: IntArray, n: Int): ByteArray? {
            if (n < 0 || n > data.size - pos[0]) return null
            val out = data.copyOfRange(pos[0], pos[0] + n)
            pos[0] += n
            return out
        }

        private fun leShort(data: ByteArray, off: Int): Int =
            (data[off].toInt() and 0xff) or ((data[off + 1].toInt() and 0xff) shl 8)

        private fun leInt(data: ByteArray, off: Int): Int =
            ByteBuffer.wrap(data, off, 4).order(ByteOrder.LITTLE_ENDIAN).int

        private fun leLong(data: ByteArray, off: Int): Long =
            ByteBuffer.wrap(data, off, 8).order(ByteOrder.LITTLE_ENDIAN).long
    }
}
