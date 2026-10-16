package com.scoreup.adb

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * SPAKE2 over edwards25519, bit-compatible with BoringSSL's `spake25519.c`, which adbd uses for
 * Wireless debugging pairing. Includes BoringSSL's "password scalar hack" (adding multiples of
 * the group order so the password scalar is a multiple of 8) because the peer relies on it.
 */
class Spake2(
    private val role: Role,
    private val myName: ByteArray,
    private val theirName: ByteArray,
    private val random: SecureRandom = SecureRandom(),
) {
    enum class Role { ALICE, BOB }

    private lateinit var privateKey: BigInteger
    private lateinit var passwordScalar: BigInteger
    private lateinit var passwordHash: ByteArray
    private lateinit var myMsg: ByteArray

    fun generateMessage(password: ByteArray): ByteArray {
        val tmp = ByteArray(64).also { random.nextBytes(it) }
        // sc_reduce then multiply by the cofactor (8).
        privateKey = Ed25519.fromLittleEndian(tmp).mod(Ed25519.L).shiftLeft(3)
        val p = Ed25519.BASE.times(privateKey)

        passwordHash = MessageDigest.getInstance("SHA-512").digest(password)
        var ps = Ed25519.fromLittleEndian(passwordHash).mod(Ed25519.L)
        var order = Ed25519.L
        for (bit in 0..2) {
            if (ps.testBit(bit)) ps += order
            order = order.shiftLeft(1)
        }
        check(ps.mod(BigInteger.valueOf(8)).signum() == 0)
        passwordScalar = ps

        val mask = (if (role == Role.ALICE) M else N).times(passwordScalar)
        myMsg = (p + mask).encode()
        return myMsg.copyOf()
    }

    /** Returns the 64-byte shared key, or throws if the peer's message is not a valid point. */
    fun processMessage(theirMsg: ByteArray): ByteArray {
        require(theirMsg.size == 32) { "bad SPAKE2 message length" }
        val qStar = Ed25519.decode(theirMsg) ?: throw IllegalArgumentException("invalid SPAKE2 point")
        val peersMask = (if (role == Role.ALICE) N else M).times(passwordScalar)
        val k = (qStar - peersMask).times(privateKey)
        val dhShared = k.encode()

        val sha = MessageDigest.getInstance("SHA-512")
        fun upd(b: ByteArray) {
            val len = ByteArray(8); var n = b.size.toLong()
            for (i in 0 until 8) { len[i] = (n and 0xff).toByte(); n = n shr 8 }
            sha.update(len); sha.update(b)
        }
        if (role == Role.ALICE) { upd(myName); upd(theirName); upd(myMsg); upd(theirMsg) }
        else { upd(theirName); upd(myName); upd(theirMsg); upd(myMsg) }
        upd(dhShared)
        upd(passwordHash)
        return sha.digest()
    }

    companion object {
        // Generated from SHA-256 of "edwards25519 point generation seed (M|N)" (see BoringSSL).
        internal val M: Ed25519.Point = Ed25519.affine(
            BigInteger("31406539342727633121250288103050113562375374900226415211311216773867585644232"),
            BigInteger("21177308356423958466833845032658859666296341766942662650232962324899758529114"),
        )
        internal val N: Ed25519.Point = Ed25519.affine(
            BigInteger("49918732221787544735331783592030787422991506689877079631459872391322455579424"),
            BigInteger("54629554431565467720832445949441049581317094546788069926228343916274969994000"),
        )
    }
}
