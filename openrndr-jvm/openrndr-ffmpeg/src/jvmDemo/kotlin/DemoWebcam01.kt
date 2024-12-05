import org.openrndr.application
import org.openrndr.ffmpeg.loadVideoDevice

/**
 * A simple demo to draw webcam input.
 */

fun main() {
    application {
        program {

            val video = loadVideoDevice()
            video.play()

            extend {
                video.draw(drawer)
            }
        }
    }
}