package ru.misha.dc2map

import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** PINE client (PCSX2 / ARMSX2): request [u32 size][u8 op][u32 addr], reply [u32 size][u8 0=OK][data]. */
class Pine(private val port: Int) {
    private var sock: Socket? = null
    private var inp: DataInputStream? = null
    private var out: OutputStream? = null
    private val req = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
    private val hdr = ByteArray(5)
    private val data = ByteArray(8)

    private fun connect() {
        val s = Socket()
        s.tcpNoDelay = true
        s.soTimeout = 2000
        s.connect(InetSocketAddress("127.0.0.1", port), 1500)
        sock = s; inp = DataInputStream(s.getInputStream()); out = s.getOutputStream()
    }

    fun close() {
        try { sock?.close() } catch (_: Exception) {}
        sock = null; inp = null; out = null
    }

    private fun request(op: Int, addr: Int, n: Int): ByteArray {
        if (sock == null) connect()
        req.clear()
        req.putInt(9); req.put(op.toByte()); req.putInt(addr)
        out!!.write(req.array(), 0, 9)
        out!!.flush()
        val i = inp!!
        i.readFully(hdr)
        val size = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN).int
        if (hdr[4].toInt() != 0) {
            if (size > 5) i.skipBytes(size - 5)
            throw IOException("PINE error")
        }
        i.readFully(data, 0, n)
        if (size > 5 + n) i.skipBytes(size - 5 - n)
        return data
    }

    fun u32(addr: Int): Long =
        ByteBuffer.wrap(request(2, addr and 3.inv(), 4), 0, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

    fun i32(addr: Int): Int = u32(addr).toInt()

    fun f32(addr: Int): Float = java.lang.Float.intBitsToFloat(i32(addr))

    /** n bytes starting at addr, read in 8-byte chunks */
    fun raw(addr: Int, n: Int): ByteArray {
        val res = ByteArray((n + 7) / 8 * 8)
        var k = 0
        while (k < n) {
            System.arraycopy(request(3, addr + k, 8), 0, res, k, 8)
            k += 8
        }
        return res
    }
}
