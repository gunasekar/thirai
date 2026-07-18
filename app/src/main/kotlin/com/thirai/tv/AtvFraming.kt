package com.thirai.tv

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Wire framing for the Android TV Remote protocol.
 *
 * Every protobuf message on the wire is prefixed with its length as a base-128
 * varint, then the raw serialized bytes. These helpers read and write one such
 * length-delimited message.
 */
object AtvFraming {

    fun writeMessage(out: OutputStream, message: ByteArray) {
        writeUVarint(out, message.size)
        out.write(message)
        out.flush()
    }

    /** Reads one framed message, or returns null on a clean end of stream. */
    fun readMessage(input: InputStream): ByteArray? {
        val length = readUVarint(input) ?: return null
        val buf = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buf, read, length - read)
            if (n < 0) throw EOFException("Stream ended mid-message ($read/$length)")
            read += n
        }
        return buf
    }

    private fun writeUVarint(out: OutputStream, value: Int) {
        var v = value
        while (true) {
            val b = v and 0x7F
            v = v ushr 7
            if (v == 0) {
                out.write(b)
                return
            }
            out.write(b or 0x80)
        }
    }

    private fun readUVarint(input: InputStream): Int? {
        var shift = 0
        var result = 0
        while (shift < 32) {
            val b = input.read()
            if (b < 0) {
                if (shift == 0) return null // clean EOF at a message boundary
                throw EOFException("Stream ended inside a varint")
            }
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
        throw IOException("Varint length prefix too long")
    }
}
