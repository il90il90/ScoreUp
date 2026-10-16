package com.scoreup.adb

import java.math.BigInteger

/**
 * Minimal edwards25519 group arithmetic (extended coordinates, BigInteger) for SPAKE2.
 * Not constant-time; it only processes ephemeral pairing secrets on the user's own device.
 */
internal object Ed25519 {
    val P: BigInteger = BigInteger.TWO.pow(255) - BigInteger.valueOf(19)
    val L: BigInteger = BigInteger.TWO.pow(252) + BigInteger("27742317777372353535851937790883648493")
    private val D: BigInteger = BigInteger.valueOf(-121665).mod(P) * BigInteger.valueOf(121666).modInverse(P) % P
    private val D2: BigInteger = D * BigInteger.TWO % P
    private val SQRT_M1: BigInteger = BigInteger.TWO.modPow((P - BigInteger.ONE) / BigInteger.valueOf(4), P)

    /** Point in extended coordinates (X:Y:Z:T), x = X/Z, y = Y/Z, xy = T/Z. */
    class Point(val x: BigInteger, val y: BigInteger, val z: BigInteger, val t: BigInteger) {
        operator fun plus(o: Point): Point {
            val a = (y - x) * (o.y - o.x) % P
            val b = (y + x) * (o.y + o.x) % P
            val c = t * D2 % P * o.t % P
            val d = z * BigInteger.TWO % P * o.z % P
            val e = b - a; val f = d - c; val g = d + c; val h = b + a
            return Point((e * f).mod(P), (g * h).mod(P), (f * g).mod(P), (e * h).mod(P))
        }

        operator fun unaryMinus() = Point((P - x).mod(P), y, z, (P - t).mod(P))
        operator fun minus(o: Point) = this + (-o)

        /** Multiplies by the full (unreduced) non-negative integer [k], as BoringSSL does. */
        fun times(k: BigInteger): Point {
            require(k.signum() >= 0)
            var result = IDENTITY
            var addend = this
            for (i in 0 until k.bitLength()) {
                if (k.testBit(i)) result += addend
                addend += addend
            }
            return result
        }

        fun encode(): ByteArray {
            val zi = z.modInverse(P)
            val ax = x * zi % P
            val ay = y * zi % P
            val n = ay.or(if (ax.testBit(0)) BigInteger.ONE.shiftLeft(255) else BigInteger.ZERO)
            return toLittleEndian(n, 32)
        }
    }

    val IDENTITY = Point(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)

    fun affine(x: BigInteger, y: BigInteger) = Point(x.mod(P), y.mod(P), BigInteger.ONE, (x * y).mod(P))

    val BASE: Point = decode(toLittleEndian(BigInteger.valueOf(4) * BigInteger.valueOf(5).modInverse(P) % P, 32))
        ?: error("base point")

    /** RFC 8032-style decoding; returns null for bytes that aren't a curve point. */
    fun decode(bytes: ByteArray): Point? {
        require(bytes.size == 32)
        val n = fromLittleEndian(bytes)
        val y = n.clearBit(255)
        if (y >= P) return null
        val sign = n.testBit(255)
        val x = recoverX(y, sign) ?: return null
        return affine(x, y)
    }

    private fun recoverX(y: BigInteger, sign: Boolean): BigInteger? {
        val yy = y * y % P
        val u = (yy - BigInteger.ONE).mod(P)
        val v = (D * yy + BigInteger.ONE) % P
        val xx = u * v.modInverse(P) % P
        var x = xx.modPow((P + BigInteger.valueOf(3)) / BigInteger.valueOf(8), P)
        if ((x * x - xx).mod(P) != BigInteger.ZERO) x = x * SQRT_M1 % P
        if ((x * x - xx).mod(P) != BigInteger.ZERO) return null
        if (x.signum() == 0 && sign) return null
        if (x.testBit(0) != sign) x = P - x
        return x
    }

    fun fromLittleEndian(b: ByteArray): BigInteger = BigInteger(1, b.reversedArray())

    fun toLittleEndian(n: BigInteger, len: Int): ByteArray {
        val be = n.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
        require(be.size <= len) { "value too large" }
        val out = ByteArray(len)
        for (i in be.indices) out[i] = be[be.size - 1 - i]
        return out
    }
}
