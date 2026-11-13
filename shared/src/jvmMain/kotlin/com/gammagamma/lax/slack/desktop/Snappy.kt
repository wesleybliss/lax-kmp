package com.gammagamma.lax.slack.desktop

/** Raw Snappy block decompression (LevelDB table blocks). */
internal object Snappy {
    private const val MAX_OUTPUT = 64 * 1024 * 1024

    fun decompress(src: ByteArray): ByteArray {
        val pos = intArrayOf(0)
        val outLen = varint32(src, pos)
        require(outLen in 1..MAX_OUTPUT) { "corrupt" }
        val out = ByteArray(outLen)
        var o = 0
        while (pos[0] < src.size) {
            val tag = src[pos[0]++].toInt() and 0xff
            when (tag and 3) {
                0 -> {
                    var len = tag ushr 2
                    if (len >= 60) {
                        val extra = len - 59
                        require(pos[0] + extra <= src.size) { "corrupt" }
                        len = 0
                        for (i in 0 until extra) {
                            len = len or ((src[pos[0] + i].toInt() and 0xff) shl (8 * i))
                        }
                        pos[0] += extra
                    }
                    len += 1
                    require(pos[0] + len <= src.size && o + len <= out.size) { "corrupt" }
                    System.arraycopy(src, pos[0], out, o, len)
                    pos[0] += len
                    o += len
                }
                else -> {
                    val kind = tag and 3
                    val len: Int
                    val offset: Int
                    when (kind) {
                        1 -> {
                            require(pos[0] + 1 <= src.size) { "corrupt" }
                            len = 4 + ((tag ushr 2) and 7)
                            offset = ((tag ushr 5) shl 8) or (src[pos[0]].toInt() and 0xff)
                            pos[0] += 1
                        }
                        2 -> {
                            require(pos[0] + 2 <= src.size) { "corrupt" }
                            len = 1 + (tag ushr 2)
                            offset = (src[pos[0]].toInt() and 0xff) or
                                ((src[pos[0] + 1].toInt() and 0xff) shl 8)
                            pos[0] += 2
                        }
                        else -> {
                            require(pos[0] + 4 <= src.size) { "corrupt" }
                            len = 1 + (tag ushr 2)
                            offset = (src[pos[0]].toInt() and 0xff) or
                                ((src[pos[0] + 1].toInt() and 0xff) shl 8) or
                                ((src[pos[0] + 2].toInt() and 0xff) shl 16) or
                                ((src[pos[0] + 3].toInt() and 0xff) shl 24)
                            pos[0] += 4
                        }
                    }
                    require(offset in 1..o && o + len <= out.size) { "corrupt" }
                    for (i in 0 until len) out[o + i] = out[o + i - offset]
                    o += len
                }
            }
        }
        require(o == out.size) { "corrupt" }
        return out
    }

    private fun varint32(src: ByteArray, pos: IntArray): Int {
        var result = 0
        var shift = 0
        while (pos[0] < src.size) {
            val b = src[pos[0]++].toInt() and 0xff
            result = result or ((b and 0x7f) shl shift)
            if (b and 0x80 == 0) return result
            if (shift >= 28) error("corrupt")
            shift += 7
        }
        error("corrupt")
    }
}
