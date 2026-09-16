@file:Suppress(
    "MagicNumber",
    "ComplexMethod",
    "CyclomaticComplexMethod",
    "LoopWithTooManyJumpStatements",
    "NestedBlockDepth",
    "LongMethod",
    "ReturnCount",
)

package com.yunfie.illustia.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.yunfie.illustia.models.pixiv.UgoiraPlaybackFrame
import com.yunfie.illustia.models.pixiv.normalizedUgoiraDelayMillis
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Animated GIF Encoder for Ugoira playback frames.
 * Generates standard GIF89a format with Netscape looping and frame-by-frame color quantization.
 */
object AnimatedGifEncoder {
    private const val MAX_ENCODE_DIMENSION = 1280
    private const val DEFAULT_SAMPLE_FACTOR = 10

    fun encode(
        frames: List<UgoiraPlaybackFrame>,
        output: OutputStream,
        maxDimension: Int = MAX_ENCODE_DIMENSION,
    ) {
        require(frames.isNotEmpty()) { "Frames must not be empty" }

        var isFirstFrame = true

        for (frame in frames) {
            val decodeOptions =
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            var bitmap =
                BitmapFactory.decodeFile(frame.filePath, decodeOptions)
                    ?: continue

            if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
                val scaledWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
                val scaledHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                    bitmap = scaled
                }
            }

            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            bitmap.recycle()

            val rgbBytes = ByteArray(width * height * 3)
            var byteIndex = 0
            for (pixel in pixels) {
                rgbBytes[byteIndex++] = ((pixel shr 16) and 0xFF).toByte() // R
                rgbBytes[byteIndex++] = ((pixel shr 8) and 0xFF).toByte() // G
                rgbBytes[byteIndex++] = (pixel and 0xFF).toByte() // B
            }

            val nq = NeuQuant(rgbBytes, rgbBytes.size, DEFAULT_SAMPLE_FACTOR)
            val colorTable = nq.process()
            val indexedPixels = ByteArray(pixels.size)
            for (i in pixels.indices) {
                val r = (pixels[i] shr 16) and 0xFF
                val g = (pixels[i] shr 8) and 0xFF
                val b = pixels[i] and 0xFF
                indexedPixels[i] = nq.map(b, g, r).toByte()
            }

            val delayHundredths =
                (normalizedUgoiraDelayMillis(frame.delayMillis) / 10L)
                    .toInt()
                    .coerceAtLeast(2)

            if (isFirstFrame) {
                writeHeader(output)
                writeLogicalScreenDescriptor(output, width, height)
                writeNetscapeExtension(output)
                isFirstFrame = false
            }

            writeGraphicControlExtension(output, delayHundredths)
            writeImageDescriptor(output, width, height)
            output.write(colorTable)
            LzwEncoder(width, height, indexedPixels, 8).encode(output)
        }

        check(!isFirstFrame) { "No frames could be decoded for animated GIF" }
        output.write(0x3B) // GIF trailer
        output.flush()
    }

    private fun writeHeader(out: OutputStream) {
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
    }

    private fun writeLogicalScreenDescriptor(
        out: OutputStream,
        width: Int,
        height: Int,
    ) {
        writeShort(out, width)
        writeShort(out, height)
        out.write(0) // No Global Color Table
        out.write(0) // Background color index
        out.write(0) // Pixel aspect ratio
    }

    private fun writeNetscapeExtension(out: OutputStream) {
        out.write(0x21) // Extension Introducer
        out.write(0xFF) // Application Extension Label
        out.write(11) // Block Size
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3) // Sub-block size
        out.write(1) // Loop sub-block ID
        writeShort(out, 0) // Loop count = 0 (infinite)
        out.write(0) // Block Terminator
    }

    private fun writeGraphicControlExtension(
        out: OutputStream,
        delayHundredths: Int,
    ) {
        out.write(0x21) // Extension Introducer
        out.write(0xF9) // Graphic Control Label
        out.write(4) // Block Size
        out.write(0x08) // Disposal Method: 2 (restore to background)
        writeShort(out, delayHundredths)
        out.write(0) // Transparent color index (none)
        out.write(0) // Block Terminator
    }

    private fun writeImageDescriptor(
        out: OutputStream,
        width: Int,
        height: Int,
    ) {
        out.write(0x2C) // Image Separator
        writeShort(out, 0) // Left
        writeShort(out, 0) // Top
        writeShort(out, width)
        writeShort(out, height)
        out.write(0x87) // Local Color Table flag + 256 colors (8 bits - 1 = 7)
    }

    private fun writeShort(
        out: OutputStream,
        value: Int,
    ) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }
}

/**
 * NeuQuant Neural-Net Quantization Algorithm
 * Adapted for 256-color palette generation.
 */
private class NeuQuant(
    private val thepicture: ByteArray,
    private val lengthcount: Int,
    private val samplefac: Int,
) {
    private val netsize = 256
    private val maxnetpos = netsize - 1
    private val netbiasshift = 4
    private val ncycles = 100

    private val intbiasshift = 16
    private val intbias = 1 shl intbiasshift
    private val gammashift = 10
    private val betashift = 10
    private val beta = intbias shr betashift
    private val betagamma = intbias shl (gammashift - betashift)

    private val initrad = netsize shr 3
    private val radiusbiasshift = 6
    private val radiusbias = 1 shl radiusbiasshift
    private val initradius = initrad * radiusbias
    private val radiusdec = 30

    private val alphabiasshift = 10
    private val initalpha = 1 shl alphabiasshift

    private val radbiasshift = 8
    private val radbias = 1 shl radbiasshift
    private val alpharadbshift = alphabiasshift + radbiasshift
    private val alpharadbias = 1 shl alpharadbshift

    private val network = Array(netsize) { DoubleArray(4) }
    private val netindex = IntArray(256)
    private val bias = IntArray(netsize)
    private val freq = IntArray(netsize)
    private val radpower = IntArray(initrad)

    init {
        for (i in 0 until netsize) {
            val v = ((i shl (netbiasshift + 8)) / netsize).toDouble()
            network[i][0] = v
            network[i][1] = v
            network[i][2] = v
            freq[i] = intbias / netsize
            bias[i] = 0
        }
    }

    fun process(): ByteArray {
        learn()
        unbiasnet()
        inxbuild()
        return colorMap()
    }

    fun map(
        b: Int,
        g: Int,
        r: Int,
    ): Int = inxsearch(b, g, r)

    private fun colorMap(): ByteArray {
        val map = ByteArray(3 * netsize)
        val index = IntArray(netsize)
        for (i in 0 until netsize) index[network[i][3].toInt()] = i
        var k = 0
        for (i in 0 until netsize) {
            val j = index[i]
            map[k++] = network[j][0].toInt().coerceIn(0, 255).toByte() // R
            map[k++] = network[j][1].toInt().coerceIn(0, 255).toByte() // G
            map[k++] = network[j][2].toInt().coerceIn(0, 255).toByte() // B
        }
        return map
    }

    private fun inxbuild() {
        var previouscol = 0
        var startpos = 0
        for (i in 0 until netsize) {
            var smallpos = i
            var smallval = network[i][1]
            for (j in i + 1 until netsize) {
                if (network[j][1] < smallval) {
                    smallpos = j
                    smallval = network[j][1]
                }
            }
            if (i != smallpos) {
                for (c in 0..3) {
                    val temp = network[smallpos][c]
                    network[smallpos][c] = network[i][c]
                    network[i][c] = temp
                }
            }
            if (smallval.toInt() != previouscol) {
                netindex[previouscol] = (startpos + i) shr 1
                for (j in previouscol + 1 until smallval.toInt().coerceAtMost(256)) netindex[j] = i
                previouscol = smallval.toInt()
                startpos = i
            }
        }
        netindex[previouscol] = (startpos + maxnetpos) shr 1
        for (j in previouscol + 1 until 256) netindex[j] = maxnetpos
    }

    private fun learn() {
        val length = lengthcount
        val samplepixels = length / (3 * samplefac)
        var delta = samplepixels / ncycles
        if (delta == 0) delta = 1
        var alpha = initalpha
        var radius = initradius

        var rad = radius shr radiusbiasshift
        if (rad <= 1) rad = 0
        for (i in 0 until rad) {
            radpower[i] = alpha * (((rad * rad - i * i) * radbias) / (rad * rad))
        }

        var step =
            if (length < 3 * 503) {
                3
            } else if (length % (3 * 499) != 0) {
                3 * 499
            } else if (length % (3 * 491) != 0) {
                3 * 491
            } else {
                3 * 487
            }
        var pix = 0
        var i = 0
        while (i < samplepixels) {
            val b = (thepicture[pix + 0].toInt() and 0xFF) shl netbiasshift
            val g = (thepicture[pix + 1].toInt() and 0xFF) shl netbiasshift
            val r = (thepicture[pix + 2].toInt() and 0xFF) shl netbiasshift
            val j = contest(b, g, r)

            altersingle(alpha, j, b, g, r)
            if (rad != 0) alterneigh(rad, j, b, g, r)

            pix += step
            if (pix >= length) pix -= length

            i++
            if (i % delta == 0) {
                alpha -= alpha / (30 + (samplefac - 1) / 3)
                radius -= radius / radiusdec
                rad = radius shr radiusbiasshift
                if (rad <= 1) rad = 0
                for (k in 0 until rad) {
                    radpower[k] = alpha * (((rad * rad - k * k) * radbias) / (rad * rad))
                }
            }
        }
    }

    private fun altersingle(
        alpha: Int,
        i: Int,
        b: Int,
        g: Int,
        r: Int,
    ) {
        val a = alpha.toDouble() / initalpha
        network[i][0] -= a * (network[i][0] - b)
        network[i][1] -= a * (network[i][1] - g)
        network[i][2] -= a * (network[i][2] - r)
    }

    private fun alterneigh(
        rad: Int,
        i: Int,
        b: Int,
        g: Int,
        r: Int,
    ) {
        var lo = i - rad
        if (lo < -1) lo = -1
        var hi = i + rad
        if (hi > netsize) hi = netsize

        var j = i + 1
        var k = i - 1
        var m = 1
        while (j < hi || k > lo) {
            val a = radpower[m++].toDouble() / alpharadbias
            if (j < hi) {
                network[j][0] -= a * (network[j][0] - b)
                network[j][1] -= a * (network[j][1] - g)
                network[j][2] -= a * (network[j][2] - r)
                j++
            }
            if (k > lo) {
                network[k][0] -= a * (network[k][0] - b)
                network[k][1] -= a * (network[k][1] - g)
                network[k][2] -= a * (network[k][2] - r)
                k--
            }
        }
    }

    private fun contest(
        b: Int,
        g: Int,
        r: Int,
    ): Int {
        var bestd = Int.MAX_VALUE
        var bestbiasd = bestd
        var bestpos = -1
        var bestbiaspos = bestpos

        for (i in 0 until netsize) {
            val dist = abs(network[i][0].toInt() - b) + abs(network[i][1].toInt() - g) + abs(network[i][2].toInt() - r)
            if (dist < bestd) {
                bestd = dist
                bestpos = i
            }
            val biasdist = dist - (bias[i] shr (intbiasshift - netbiasshift))
            if (biasdist < bestbiasd) {
                bestbiasd = biasdist
                bestbiaspos = i
            }
            val betafreq = freq[i] shr betashift
            freq[i] -= betafreq
            bias[i] += betafreq shl gammashift
        }
        freq[bestpos] += beta
        bias[bestpos] -= betagamma
        return bestbiaspos
    }

    private fun unbiasnet() {
        for (i in 0 until netsize) {
            network[i][0] = (network[i][0].toInt() shr netbiasshift).toDouble()
            network[i][1] = (network[i][1].toInt() shr netbiasshift).toDouble()
            network[i][2] = (network[i][2].toInt() shr netbiasshift).toDouble()
            network[i][3] = i.toDouble()
        }
    }

    private fun inxsearch(
        b: Int,
        g: Int,
        r: Int,
    ): Int {
        var bestd = 1000
        var best = -1
        var i = netindex[g]
        var j = i - 1

        while (i < netsize || j >= 0) {
            if (i < netsize) {
                val distG = network[i][1].toInt() - g
                if (distG >= bestd) {
                    i = netsize
                } else {
                    val dist = abs(network[i][0].toInt() - b) + abs(distG) + abs(network[i][2].toInt() - r)
                    if (dist < bestd) {
                        bestd = dist
                        best = network[i][3].toInt()
                    }
                    i++
                }
            }
            if (j >= 0) {
                val distG = g - network[j][1].toInt()
                if (distG >= bestd) {
                    j = -1
                } else {
                    val dist = abs(network[j][0].toInt() - b) + abs(distG) + abs(network[j][2].toInt() - r)
                    if (dist < bestd) {
                        bestd = dist
                        best = network[j][3].toInt()
                    }
                    j--
                }
            }
        }
        return best
    }
}

/**
 * GIF LZW Encoder.
 * Compresses indexed color pixels into standard GIF data blocks.
 */
private class LzwEncoder(
    private val imgW: Int,
    private val imgH: Int,
    private val pixAry: ByteArray,
    private val initCodeSize: Int,
) {
    private val maxbits = 12
    private val maxmaxcode = 1 shl maxbits
    private val hsize = 5003

    private val htab = IntArray(hsize)
    private val codetab = IntArray(hsize)

    private var nBits = 0
    private var maxcode = 0
    private var clearFlag = false

    private val clearCode = 1 shl initCodeSize
    private val eofCode = clearCode + 1
    private var freeEnt = 0

    private var curAccum = 0
    private var curBits = 0
    private val accum = ByteArray(256)
    private var aCount = 0

    private var curPixel = 0

    private val masks =
        intArrayOf(
            0x0000,
            0x0001,
            0x0003,
            0x0007,
            0x000F,
            0x001F,
            0x003F,
            0x007F,
            0x00FF,
            0x01FF,
            0x03FF,
            0x07FF,
            0x0FFF,
            0x1FFF,
            0x3FFF,
            0x7FFF,
            0xFFFF,
        )

    fun encode(outs: OutputStream) {
        outs.write(initCodeSize)
        curPixel = 0
        compress(initCodeSize + 1, outs)
        outs.write(0) // Block terminator
    }

    private fun charOut(
        c: Byte,
        outs: OutputStream,
    ) {
        accum[aCount++] = c
        if (aCount >= 254) flushChar(outs)
    }

    private fun flushChar(outs: OutputStream) {
        if (aCount > 0) {
            outs.write(aCount)
            outs.write(accum, 0, aCount)
            aCount = 0
        }
    }

    private fun output(
        code: Int,
        outs: OutputStream,
    ) {
        curAccum = curAccum or (code and masks[nBits] shl curBits)
        curBits += nBits

        while (curBits >= 8) {
            charOut((curAccum and 0xFF).toByte(), outs)
            curAccum = curAccum shr 8
            curBits -= 8
        }

        if (freeEnt > maxcode || clearFlag) {
            if (clearFlag) {
                maxcode = (1 shl nBits) - 1
                clearFlag = false
            } else {
                nBits++
                maxcode = if (nBits == maxbits) maxmaxcode else (1 shl nBits) - 1
            }
        }

        if (code == eofCode) {
            while (curBits > 0) {
                charOut((curAccum and 0xFF).toByte(), outs)
                curAccum = curAccum shr 8
                curBits -= 8
            }
            flushChar(outs)
        }
    }

    private fun clBlock(outs: OutputStream) {
        for (i in 0 until hsize) htab[i] = -1
        freeEnt = clearCode + 2
        clearFlag = true
        output(clearCode, outs)
    }

    private fun compress(
        initBits: Int,
        outs: OutputStream,
    ) {
        nBits = initBits
        maxcode = (1 shl nBits) - 1
        freeEnt = clearCode + 2
        clearFlag = false

        for (i in 0 until hsize) htab[i] = -1
        output(clearCode, outs)

        var ent = nextPixel()
        val hshift = 4
        var fcode: Int

        while (true) {
            val c = nextPixel()
            if (c == -1) break

            fcode = (c shl maxbits) + ent
            var i = (c shl hshift) xor ent

            if (htab[i] == fcode) {
                ent = codetab[i]
                continue
            } else if (htab[i] >= 0) {
                var disp = hsize - i
                if (i == 0) disp = 1
                var matched = false
                while (true) {
                    i -= disp
                    if (i < 0) i += hsize
                    if (htab[i] == fcode) {
                        ent = codetab[i]
                        matched = true
                        break
                    }
                    if (htab[i] < 0) break
                }
                if (matched) continue
            }

            output(ent, outs)
            ent = c
            if (freeEnt < maxmaxcode) {
                codetab[i] = freeEnt++
                htab[i] = fcode
            } else {
                clBlock(outs)
            }
        }

        output(ent, outs)
        output(eofCode, outs)
    }

    private fun nextPixel(): Int {
        if (curPixel >= imgW * imgH) return -1
        return pixAry[curPixel++].toInt() and 0xFF
    }
}
