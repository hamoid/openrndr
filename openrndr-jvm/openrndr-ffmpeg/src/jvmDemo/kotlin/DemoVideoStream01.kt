import org.openrndr.application
import org.openrndr.ffmpeg.ScreenRecorder
import org.openrndr.ffmpeg.VideoWriterProfile
import org.openrndr.math.Polar

/**
 * Demonstrates how to use the VideoWriter with a custom VideoWriterProfile
 * to stream the visual output to a receiver program (tested successfully
 * with OBS Studio and ffplay, unsuccessfully with VLC).
 */

fun main() {
    application {
        program {
            val frameRate = 60
            class StreamingProfile : VideoWriterProfile() {
                override fun arguments() = arrayOf(
                    "-f", "rawvideo",
                    "-vcodec", "rawvideo",
                    "-s", "${width}x${height}",
                    "-pix_fmt", "rgba",
                    "-r", "$frameRate",
                    "-i", "-",
                    "-an",
                    "-r", "$frameRate", // repeat?
                    "-vf", "vflip",
                    "-vcodec", "libx264",
                    "-preset", "ultrafast",
                    "-pix_fmt", "yuv420p",
                    "-tune", "zerolatency",
                    "-f", "mpegts",
                    "udp://127.0.0.1:1234"
                )

                override val fileExtension = ""
            }

            val sr = ScreenRecorder().also {
                it.includeDefaultArguments = false
                it.profile = StreamingProfile()
                it.frameRate = frameRate
                it.frameClock = false // better false when streaming?
            }

            extend(sr)
            extend {
                val pos = Polar(seconds * 50.0, 200.0).cartesian + drawer.bounds.center
                drawer.circle(pos, 100.0)
            }
        }
    }
}