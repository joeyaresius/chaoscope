package com.chaoscope

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.Image
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
 */
object VideoExporter {

    private const val MIME_TYPE         = "video/avc"
    private const val BIT_RATE          = 8_000_000   // 8 Mbps
    private const val I_FRAME_INTERVAL  = 1            // keyframe every second

    /**
     * Export an animation to MP4.
     *
     * @param context      Application context.
     * @param frameCount   Total number of frames.
     * @param fps          Target frame rate.
     * @param frameSize    Width = height of each (square) frame in pixels.
     * @param renderFrame  Suspend lambda that produces one [Bitmap] for [frameIndex].
     *                     Return null to skip a frame (export continues).
     * @param onProgress   Called with (framesEncoded, totalFrames) after each frame.
     * @return MediaStore URI string of the saved MP4.
     */
    suspend fun export(
        context:     Context,
        frameCount:  Int,
        fps:         Int = 30,
        frameSize:   Int = 768,
        renderFrame: suspend (frameIndex: Int) -> Bitmap?,
        onProgress:  (Int, Int) -> Unit = { _, _ -> },
    ): String {
        require(frameCount > 0) { "frameCount must be > 0" }

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

        // MediaMuxer requires a seekable file to write the moov atom.
        // ContentResolver FDs from openFileDescriptor() are NOT seekable on many
        // devices, producing a corrupt/empty container.  Write to a local temp file
        // first, then copy the completed MP4 to the MediaStore URI.
        val tempFile = File(context.cacheDir, "chaoscope_export_${System.currentTimeMillis()}.mp4")
        try {
            encode(
                filePath    = tempFile.absolutePath,
                frameCount  = frameCount,
                fps         = fps,
                frameSize   = frameSize,
                renderFrame = renderFrame,
                onProgress  = onProgress,
            )

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
        frameSize:   Int,
        renderFrame: suspend (frameIndex: Int) -> Bitmap?,
        onProgress:  (Int, Int) -> Unit,
    ) {
        val format = MediaFormat.createVideoFormat(MIME_TYPE, frameSize, frameSize).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            )
            setInteger(MediaFormat.KEY_BIT_RATE,         BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE,        fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,  I_FRAME_INTERVAL)
        }

        val encoder = MediaCodec.createEncoderByType(MIME_TYPE)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer  = MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val info   = MediaCodec.BufferInfo()
        var trackIdx    = -1
        var muxerActive = false
        val usPerFrame  = 1_000_000L / fps

        // Local drain helper — captures mutable encoder/muxer state by closure.
        fun drain(endOfStream: Boolean) {
            var retries = if (endOfStream) 200 else 0
            while (true) {
                val outIdx = encoder.dequeueOutputBuffer(info, 10_000L)
                when {
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!endOfStream) return
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

                // Feed to encoder input
                val inputIdx = encoder.dequeueInputBuffer(10_000L)
                if (inputIdx >= 0) {
                    encoder.getInputImage(inputIdx)?.let { image ->
                        writeBitmapToYuv(bitmap, image)
                    }
                    encoder.queueInputBuffer(
                        inputIdx, 0, 0,
                        frameIdx * usPerFrame,
                        0,
                    )
                }
                bitmap.recycle()

                // Drain whatever output is ready without blocking
                drain(endOfStream = false)

                encoded++
                onProgress(encoded, frameCount)
            }

            // Signal end-of-stream and drain remaining output
            val eosIdx = encoder.dequeueInputBuffer(10_000L)
            if (eosIdx >= 0) {
                encoder.queueInputBuffer(
                    eosIdx, 0, 0,
                    frameCount * usPerFrame,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
            }
            drain(endOfStream = true)
        } finally {
            encoder.stop()
            encoder.release()
            if (muxerActive) muxer.stop()
            muxer.release()
        }
    }

    // ── Bitmap → YUV420Flexible (handles NV12, I420, etc. via plane strides) ──

    private fun writeBitmapToYuv(src: Bitmap, image: Image) {
        val w = image.width
        val h = image.height

        // Scale source if the sizes differ (shouldn't normally happen)
        val bmp = if (src.width == w && src.height == h) src
                  else Bitmap.createScaledBitmap(src, w, h, true)
        val argb = IntArray(w * h)
        bmp.getPixels(argb, 0, w, 0, 0, w, h)
        if (bmp !== src) bmp.recycle()

        val yPlane  = image.planes[0]
        val uPlane  = image.planes[1]
        val vPlane  = image.planes[2]
        val yBuf    = yPlane.buffer
        val uBuf    = uPlane.buffer
        val vBuf    = vPlane.buffer
        val yRow    = yPlane.rowStride
        val uvRow   = uPlane.rowStride
        val uvPixel = uPlane.pixelStride  // 1 for I420, 2 for NV12

        for (row in 0 until h) {
            for (col in 0 until w) {
                val p = argb[row * w + col]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8)  and 0xFF
                val b =  p         and 0xFF

                // BT.601 full-range → studio-swing Y
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yBuf.put(row * yRow + col, y.coerceIn(16, 235).toByte())

                // Subsample UV 4:2:0
                if (row % 2 == 0 && col % 2 == 0) {
                    val u  = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v  = ((112 * r - 94 * g -  18 * b + 128) shr 8) + 128
                    val pos = (row / 2) * uvRow + (col / 2) * uvPixel
                    uBuf.put(pos, u.coerceIn(16, 240).toByte())
                    vBuf.put(pos, v.coerceIn(16, 240).toByte())
                }
            }
        }
    }
}
