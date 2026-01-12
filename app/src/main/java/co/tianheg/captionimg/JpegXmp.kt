package co.tianheg.captionimg

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.min

/**
 * Minimal JPEG XMP (APP1) reader/writer.
 *
 * We store XMP in an APP1 segment whose payload begins with:
 * "http://ns.adobe.com/xap/1.0/\u0000"
 */
object JpegXmp {

    private val SOI = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    private const val MARKER_PREFIX: Int = 0xFF

    private const val APP1: Int = 0xE1
    private const val SOS: Int = 0xDA
    private const val EOI: Int = 0xD9

    private val XMP_ID = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)

    fun isJpeg(header2: ByteArray): Boolean {
        return header2.size >= 2 && header2[0] == SOI[0] && header2[1] == SOI[1]
    }

    /**
     * Reads the raw XMP packet XML from a JPEG, or null if not present.
     */
    fun readXmp(input: InputStream): String? {
        val bis = BufferedInputStream(input)
        val soi = ByteArray(2)
        readFully(bis, soi)
        if (!isJpeg(soi)) return null

        while (true) {
            val marker = readMarker(bis) ?: return null
            val type = marker and 0xFF

            if (type == SOS) {
                // Image data begins; no more metadata segments beyond this.
                return null
            }
            if (type == EOI) return null

            if (!hasLength(type)) {
                continue
            }

            val length = readU16be(bis)
            val payloadLen = length - 2
            if (payloadLen < 0) throw EOFException("Invalid segment length")

            if (type == APP1) {
                val prefix = ByteArray(min(payloadLen, XMP_ID.size))
                readFully(bis, prefix)

                if (prefix.size == XMP_ID.size && prefix.contentEquals(XMP_ID)) {
                    val rest = ByteArray(payloadLen - XMP_ID.size)
                    readFully(bis, rest)
                    return rest.toString(Charsets.UTF_8)
                }

                // Not XMP; skip remaining payload bytes.
                val remaining = payloadLen - prefix.size
                if (remaining > 0) skipFully(bis, remaining.toLong())
            } else {
                skipFully(bis, payloadLen.toLong())
            }
        }
    }

    /**
     * Writes/updates the XMP APP1 segment in a JPEG.
     *
     * - Removes existing XMP APP1 segments
     * - Inserts the new XMP segment right after SOI (safe and widely compatible)
     */
    fun writeXmp(input: InputStream, output: OutputStream, xmpPacket: String): Boolean {
        val bis = BufferedInputStream(input)
        val bos = BufferedOutputStream(output)

        val soi = ByteArray(2)
        readFully(bis, soi)
        if (!isJpeg(soi)) return false

        bos.write(soi)

        // Insert new XMP segment immediately after SOI.
        writeXmpSegment(bos, xmpPacket)

        while (true) {
            val marker = readMarker(bis) ?: break
            val type = marker and 0xFF

            // Write markers without length directly.
            if (!hasLength(type)) {
                bos.write(0xFF)
                bos.write(type)
                if (type == EOI) break
                continue
            }

            val length = readU16be(bis)
            val payloadLen = length - 2
            if (payloadLen < 0) return false

            val payload = ByteArray(payloadLen)
            readFully(bis, payload)

            // Skip existing XMP APP1 segments.
            if (type == APP1 && payload.size >= XMP_ID.size) {
                val prefix = payload.copyOfRange(0, XMP_ID.size)
                if (prefix.contentEquals(XMP_ID)) {
                    continue
                }
            }

            // Copy the segment as-is.
            bos.write(0xFF)
            bos.write(type)
            writeU16be(bos, length)
            bos.write(payload)

            if (type == SOS) {
                // After SOS, the rest is entropy-coded image data until EOI.
                bis.copyTo(bos)
                break
            }
        }

        bos.flush()
        return true
    }

    private fun writeXmpSegment(out: OutputStream, xmpPacket: String) {
        val xmpBytes = xmpPacket.toByteArray(Charsets.UTF_8)
        val payloadLen = XMP_ID.size + xmpBytes.size

        // JPEG segment length field is 16-bit (includes the 2 length bytes).
        val totalLen = payloadLen + 2
        require(totalLen <= 0xFFFF) { "XMP packet too large for a single APP1 segment" }

        out.write(0xFF)
        out.write(APP1)
        writeU16be(out, totalLen)
        out.write(XMP_ID)
        out.write(xmpBytes)
    }

    private fun hasLength(type: Int): Boolean {
        return when (type) {
            0xD8, // SOI
            0xD9, // EOI
            in 0xD0..0xD7, // RST
            0x01 -> false // TEM

            else -> true
        }
    }

    private fun readMarker(input: InputStream): Int? {
        var b: Int
        do {
            b = input.read()
            if (b == -1) return null
        } while (b != MARKER_PREFIX)

        // Skip fill bytes (0xFF...) per spec.
        do {
            b = input.read()
            if (b == -1) return null
        } while (b == MARKER_PREFIX)

        return b
    }

    private fun readU16be(input: InputStream): Int {
        val hi = input.read()
        val lo = input.read()
        if (hi == -1 || lo == -1) throw EOFException("Unexpected EOF")
        return (hi shl 8) or lo
    }

    private fun writeU16be(out: OutputStream, value: Int) {
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var off = 0
        while (off < buffer.size) {
            val read = input.read(buffer, off, buffer.size - off)
            if (read == -1) throw EOFException("Unexpected EOF")
            off += read
        }
    }

    private fun skipFully(input: InputStream, bytes: Long) {
        var remaining = bytes
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() == -1) throw EOFException("Unexpected EOF")
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }
}

object XmpDescription {
    fun buildPacket(description: String): String {
        val escaped = escapeXml(description)
        return """
                        <?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>
                        <x:xmpmeta xmlns:x="adobe:ns:meta/">
                            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                                <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <dc:description>
                    <rdf:Alt>
                                            <rdf:li xml:lang="x-default">$escaped</rdf:li>
                    </rdf:Alt>
                  </dc:description>
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
                        <?xpacket end="w"?>
        """.trimIndent()
    }

    fun extractDescription(xmp: String): String? {
        // Try x-default first.
                val start1 = "<rdf:li xml:lang=\"x-default\">"
                // Legacy packets accidentally contained literal backslashes (written from a Kotlin raw string).
                val start1Legacy = "<rdf:li xml:lang=\\\"x-default\\\">"
        val end = "</rdf:li>"
        var idx = xmp.indexOf(start1)
        val matchedStart = if (idx != -1) {
            start1
        } else {
            idx = xmp.indexOf(start1Legacy)
            if (idx != -1) start1Legacy else null
        }
        if (idx != -1) {
            val contentStart = idx + (matchedStart?.length ?: start1.length)
            val endIdx = xmp.indexOf(end, contentStart)
            if (endIdx != -1) {
                val raw = xmp.substring(contentStart, endIdx).trim()
                return unescapeXml(raw).takeIf { it.isNotBlank() }
            }
        }

        // Fallback to first li.
        val start2 = "<rdf:li>"
        idx = xmp.indexOf(start2)
        if (idx != -1) {
            val contentStart = idx + start2.length
            val endIdx = xmp.indexOf(end, contentStart)
            if (endIdx != -1) {
                val raw = xmp.substring(contentStart, endIdx).trim()
                return unescapeXml(raw).takeIf { it.isNotBlank() }
            }
        }

        return null
    }

    private fun escapeXml(s: String): String {
        return s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun unescapeXml(s: String): String {
        return s
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&gt;", ">")
            .replace("&lt;", "<")
            .replace("&amp;", "&")
    }
}
