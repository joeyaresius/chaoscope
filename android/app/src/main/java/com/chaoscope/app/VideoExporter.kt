package com.chaoscope

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.isActive
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Encodes a sequence of bitmaps into an H.264/MP4 and saves to
 * MediaStore (Movies/Chaoscope/).  All work must run off the main thread.
 *
 * Frames are rendered one-by-one via [renderFrame] and fed straight to
 * MediaCodec, so only one decoded bitmap lives in memory at a time.
 *
 * Implementation notes:
 *  - Uses COLOR_FormatYUV420SemiPlanar (NV12) + getInputBuffer() for
 *    maximum hardware-encoder compatibility.  The flexible Image API
 *    (getInputImage) returns null on many hardware codecs.
 *  - MediaMuxer is given a local cacheDir temp path so it can seek back
 *    to write the moov atom on stop().  ContentResolver FDs are not
 *    seekable on many devices, producing a corrupt/empty container.
 *    The finished file is copied to MediaStore via openOutputStream().
 */
object VideoExporter {

    private const val MIME_TYPE        = "video/avc"
    // Bitrate scales with pixel throughput (~0.45 bits/pixel/frame — the ratio the
    // old fixed 8 Mbps gave at 768²@30), clamped to a sane range for phone H.264.
    private const val BITS_PER_PIXEL   = 0.45f
    private const val MIN_BIT_RATE     = 4_000_000
    private const val MAX_BIT_RATE     = 20_000_000
    private const val I_FRAME_INTERVAL = 1            // keyframe every second

    /**
     * Export an animation to MP4.
     *
     * @param context      Application context.
     * @param frameCount   Total number of frames.
     * @param fps          Target frame rate.
     * @param width        Frame width in pixels (must be even).
     * @param height       Frame height in pixels (must be even).
     * @param renderFrame  Suspend lambda that produces one [Bitmap] for [frameIndex].
     *                     Return null to skip a frame (export continues).
     * @param onProgress   Called with (framesEncoded, totalFrames) after each frame.
     * @return MediaStore URI string of the saved MP4.
     */
    suspend fun export(
        context:     Context,
        frameCount:  Int,
        fps:         Int = 30,
        width:       Int = 768,
        height:      Int = 768,
        renderFrame: suspend (frameIndex: Int) -> Bitmap?,
        onProgress:  (Int, Int) -> Unit = { _, _ -> },
    ): String {
        require(frameCount > 0) { "frameCount must be > 0" }
        require(width % 2 == 0 && height % 2 == 0) { "frame dimensions must be even" }

        // ── Create MediaStore entry ────────────────────────────────────────────
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME,
                "chaoscope_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/Chaoscope")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver
            .insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore refused to create the MP4 entry.")

        // MediaMuxer needs a seekable file to write the moov atom at stop().
        // ContentResolver FDs are NOT seekable on many devices → use a cacheDir
        // temp file, then copy the completed MP4 to the MediaStore URI.
        val tempFile = File(
            context.cacheDir,
            "chaoscope_export_${System.currentTimeMillis()}.mp4",
        )
        try {
            encode(
                filePath    = tempFile.absolutePath,
                frameCount  = frameCount,
                fps         = fps,
                width       = width,
                height      = height,
                renderFrame = renderFrame,
                onProgress  = onProgress,
            )

            // If the coroutine was cancelled mid-encode, abort before saving
            // so no partial video ends up in the user's Movies folder.
            if (!coroutineContext.isActive) {
                throw kotlinx.coroutines.CancellationException("Export cancelled")
            }

            // Copy the finished temp file to the MediaStore URI
            context.contentResolver.openOutputStream(uri)?.use { out ->
                tempFile.inputStream().use { it.copyTo(out) }
            } ?: throw IllegalStateException("Could not open output stream for MP4.")

            // Publish completed file (API 29+ pending flag)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                    null, null,
                )
            }
        } catch (e: Exception) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw e
        } finally {
            tempFile.delete()
        }

        return uri.toString()
    }

    // ── Core encoder/muxer pipeline ───────────────────────────────────────────

    private suspend fun encode(
        filePath:    String,
        frameCount:  Int,
        fps:         Int,
        width:       Int,
        height:      Int,
        renderFrame: suspend (frameIndex: Int) -> Bitmap?,
        onProgress:  (Int, Int) -> Unit,
    ) {
        val bitRate = (BITS_PER_PIXEL * width * height * fps).toInt()
            .coerceIn(MIN_BIT_RATE, MAX_BIT_RATE)

        // NV12 (COLOR_FormatYUV420SemiPlanar) is supported by every Android
        // hardware encoder since API 16.  The flexible format + getInputImage()
        // approach silently returns null on many hardware codecs, so we write
        // directly to the input ByteBuffer in NV12 layout instead.
        val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            )
            setInteger(MediaFormat.KEY_BIT_RATE,        bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE,       fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }

        val encoder = MediaCodec.createEncoderByType(MIME_TYPE)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer       = MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val info        = MediaCodec.BufferInfo()
        var trackIdx    = -1
        var muxerActive = false
        val usPerFrame  = 1_000_000L / fps
        val yuvSize     = width * height * 3 / 2   // NV12 byte count

        // Local drain helper — captures mutable encoder/muxer state by closure.
        fun drain(endOfStream: Boolean) {
            var retries = if (endOfStream) 300 else 1
            while (true) {
                val outIdx = encoder.dequeueOutputBuffer(info, 10_000L)
                when {
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (--retries <= 0) return
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerActive) {
                            trackIdx    = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerActive = true
                        }
                    }
                    outIdx >= 0 -> {
                        // Discard codec-config packets (SPS/PPS embedded by muxer)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            encoder.releaseOutputBuffer(outIdx, false)
                            continue
                        }
                        if (muxerActive && info.size > 0) {
                            muxer.writeSampleData(
                                trackIdx,
                                encoder.getOutputBuffer(outIdx)!!,
                                info,
                            )
                        }
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        encoder.releaseOutputBuffer(outIdx, false)
                        if (eos) return
                    }
                }
            }
        }

        try {
            var encoded = 0
            for (frameIdx in 0 until frameCount) {
                if (!coroutineContext.isActive) break

                // Render this frame; skip if null (attractor diverged etc.)
                val bitmap = renderFrame(frameIdx) ?: continue

                // Obtain an input buffer, write NV12 pixel data, submit it
                val inputIdx = encoder.dequeueInputBuffer(10_000L)
                if (inputIdx >= 0) {
                    val buf = encoder.getInputBuffer(inputIdx)
                        ?: throw IllegalStateException("getInputBuffer returned null")
                    writeBitmapToNV12(bitmap, buf, width, height)
                    encoder.queueInputBuffer(
                        inputIdx, 0, yuvSize,
                        frameIdx * usPerFrame,
                        0,
                    )
                }
                bitmap.recycle()

                // Drain whatever output is ready (≤1 retry — non-blocking)
                drain(endOfStream = false)

                encoded++
                onProgress(encoded, frameCount)
            }

            // Only send EOS + drain if we encoded all frames normally.
            // Skipping this on cancellation avoids blocking in the drain loop.
            if (coroutineContext.isActive) {
                val eosIdx = encoder.dequeueInputBuffer(10_000L)
                if (eosIdx >= 0) {
                    encoder.getInputBuffer(eosIdx)?.clear()
                    encoder.queueInputBuffer(
                        eosIdx, 0, 0,
                        frameCount * usPerFrame,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                    )
                }
                drain(endOfStream = true)
            }
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            if (muxerActive) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    // ── Bitmap → NV12 (YUV420SemiPlanar) ByteBuffer ───────────────────────────
    //
    // NV12 layout:
    //   Offset 0          : Y plane  — one byte per pixel, row-major
    //   Offset W*H        : UV plane — interleaved (U, V) pairs for each 2×2 block
    //   Total size        : W * H * 3 / 2 bytes

    private fun writeBitmapToNV12(src: Bitmap, buf: java.nio.ByteBuffer, w: Int, h: Int) {
        // Scale if the bitmap doesn't exactly match the encode dimensions
        val bmp = if (src.width == w && src.height == h) src
                  else Bitmap.createScaledBitmap(src, w, h, true)
        val argb = IntArray(w * h)
        bmp.getPixels(argb, 0, w, 0, 0, w, h)
        if (bmp !== src) bmp.recycle()

        buf.clear()

        // Y plane ── BT.601 studio-swing luma
        for (i in 0 until w * h) {
            val p = argb[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8)  and 0xFF
            val b =  p         and 0xFF
            val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
            buf.put(y.coerceIn(16, 235).toByte())
        }

        // UV plane ── BT.601 studio-swing chroma, 4:2:0 subsampled, interleaved U,V
        for (row in 0 until h / 2) {
            for (col in 0 until w / 2) {
                val p = argb[row * 2 * w + col * 2]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8)  and 0xFF
                val b =  p         and 0xFF
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r -  94 * g -  18 * b + 128) shr 8) + 128
                buf.put(u.coerceIn(16, 240).toByte())
                buf.put(v.coerceIn(16, 240).toByte())
            }
        }
    }
}
