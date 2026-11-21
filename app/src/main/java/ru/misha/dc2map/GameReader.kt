package ru.misha.dc2map

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Port of dc2_map_server.py v1: Dark Cloud 2 (SCUS-97213) dungeon state -> JSON for map.html */
class GameReader(port: Int) {
    private val p = Pine(port)
    private var grid: JSONObject? = null
    private var tick = 0

    companion object {
        const val MAINCHARA = 0x3772C8
        const val AUTOMAP = 0x1EA0480
        const val MONSTERMAN = 0x3772A8
        const val TREASURE = 0x3772C4
        const val GEOSTONE = 0x1EA51C0
        const val RNDCIRCLE = 0x1EA4B20
        const val SAVEDATA_PTR = 0x376FE4
        const val BATTLEAREA = 0x1DDB1F0
        const val FLOORMGR = BATTLEAREA + 20
        val FLOORS_PER_DNG = intArrayOf(9, 16, 25, 21, 23, 29, 39)
    }

    private fun readGrid(): JSONObject? {
        val wh = p.u32(AUTOMAP + 440)
        val w = (wh and 0xFFFF).toInt()
        val h = (wh shr 16).toInt()
        val sx = p.f32(AUTOMAP + 444)
        val sz = p.f32(AUTOMAP + 448)
        val gp = p.i32(AUTOMAP + 460)
        if (gp == 0 || w !in 1..64 || h !in 1..64 || sx <= 0f || sz <= 0f) return null
        val bytes = p.raw(gp, w * h * 28)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val cells = JSONArray()
        for (k in 0 until w * h) {
            val o = k * 28
            val t = bb.getShort(o).toInt()
            val part = bb.getShort(o + 4).toInt()
            val links = bb.getShort(o + 10).toInt()
            val vis = bb.getShort(o + 12).toInt()
            cells.put(JSONArray().put(t).put(part).put((links shr 8) and 15).put(links and 15).put(if (vis != 0) 1 else 0))
        }
        return JSONObject().put("w", w).put("h", h).put("sx", sx.toDouble()).put("sz", sz.toDouble()).put("cells", cells)
    }

    private fun obj(k: String, x: Float, z: Float, seen: Boolean) =
        JSONObject().put("k", k).put("x", x.toDouble()).put("z", z.toDouble()).put("seen", seen)

    private fun readObjects(): JSONArray {
        val out = JSONArray()
        val mm = p.i32(MONSTERMAN)
        if (mm != 0) {
            val hdr = p.i32(mm)
            val reveal = hdr != 0 && (p.u32(hdr + 12276) and 2L) != 0L
            for (i in 0 until 24) {
                val m = p.i32(mm + 1156 + 4 * i)
                if (m != 0 && (p.u32(m + 1672) shr 16) == 2L && (p.u32(m + 1840) and 0xFFFF) != 1L) {
                    val seen = p.f32(m + 4852) <= p.f32(m + 4860)
                    out.put(obj("mon", p.f32(m + 16), p.f32(m + 24), seen || reveal))
                }
            }
        }
        val tb = p.i32(TREASURE)
        if (tb != 0) {
            for (i in 0 until 24) {
                val e = tb + 112 * i
                if ((p.u32(e + 100) and 0xFF) == 1L) out.put(obj("box", p.f32(e + 32), p.f32(e + 40), true))
            }
        }
        if (p.u32(GEOSTONE + 1632) != 0L) out.put(obj("geo", p.f32(GEOSTONE + 16), p.f32(GEOSTONE + 24), true))
        for (i in 0 until 3) {
            if (p.u32(RNDCIRCLE + 48 + 4 * i) != 0L)
                out.put(obj("circle", p.f32(RNDCIRCLE + 16 * i), p.f32(RNDCIRCLE + 16 * i + 8), true))
        }
        return out
    }

    private fun readFloor(): JSONObject? {
        val sd = p.i32(SAVEDATA_PTR)
        if (sd == 0) return null
        val sdd = sd + 0x1C5B4
        val dng = p.u32(sdd)
        if (dng > 6) return null
        val d = dng.toInt()
        val floor = p.u32(sdd + 4 + 4 * d)
        val fs = sdd + 60 + 20 * (FLOORS_PER_DNG.take(d).sum() + floor.toInt())
        val glid = p.i32(FLOORMGR + 4)
        val n = p.u32(FLOORMGR + 8)
        var ent = 0
        for (k in 0 until minOf(n, 64L).toInt()) {
            val e = glid + 112 * k
            if ((p.u32(e) and 0xFFFF) == 1L && (p.u32(e + 40) and 0xFF) == floor) { ent = e; break }
        }
        if (ent == 0) return null
        val w = p.u32(ent + 56)
        val ptype = ((w shr 16) and 0xFF).toInt().toByte().toInt()   // signed byte at +58
        val flags = (p.u32(fs + 14) shr (8 * ((fs + 14) and 3))) and 0xFFFF
        val elapsed = maxOf(0L, p.u32(sd + 6656) - p.u32(BATTLEAREA + 144))
        return JSONObject()
            .put("dungeon", d).put("floor", floor).put("best", p.u32(fs + 4)).put("flags", flags)
            .put("limit", p.u32(ent + 48)).put("ptype", ptype).put("pparam", p.u32(ent + 60))
            .put("elapsed", elapsed).put("mask", p.u32(BATTLEAREA + 152)).put("allk", p.u32(BATTLEAREA + 92))
    }

    fun state(): String = try {
        if (tick % 10 == 0 || grid == null) grid = readGrid()
        tick++
        val g = grid
        if (g == null) {
            JSONObject().put("ok", true).put("grid", JSONObject.NULL).toString()
        } else {
            val ch = p.i32(MAINCHARA)
            val hero = if (ch != 0) JSONObject().put("x", p.f32(ch + 16).toDouble())
                .put("z", p.f32(ch + 24).toDouble()).put("a", p.f32(ch + 36).toDouble()) else JSONObject.NULL
            JSONObject().put("ok", true).put("grid", g).put("hero", hero)
                .put("objs", readObjects()).put("floor", readFloor() ?: JSONObject.NULL).toString()
        }
    } catch (e: Exception) {
        p.close()
        grid = null
        JSONObject().put("ok", false).put("err", "no PINE connection: ${e.javaClass.simpleName}").toString()
    }
}
