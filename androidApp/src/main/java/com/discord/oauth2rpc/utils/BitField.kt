package com.discord.oauth2rpc.utils

open class BitField(
    var bitfield: Long = 0L,
) {
    fun add(vararg bits: Long): BitField {
        for (bit in bits) {
            bitfield = bitfield or bit
        }
        return this
    }

    fun remove(vararg bits: Long): BitField {
        for (bit in bits) {
            bitfield = bitfield and bit.inv()
        }
        return this
    }

    fun has(bit: Long): Boolean = (bitfield and bit) == bit

    fun missing(vararg bits: Long): List<Long> = bits.filter { !has(it) }

    fun serialize(): Long = bitfield

    override fun toString(): String = bitfield.toString()
}
