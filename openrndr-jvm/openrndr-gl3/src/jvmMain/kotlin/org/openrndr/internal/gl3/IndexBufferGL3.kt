package org.openrndr.internal.gl3

import io.github.oshai.kotlinlogging.KotlinLogging
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL33C.*
import org.lwjgl.opengl.GL44C.GL_DYNAMIC_STORAGE_BIT
import org.lwjgl.opengl.GL44C.glBufferStorage
import org.openrndr.draw.IndexBuffer
import org.openrndr.draw.IndexType
import org.openrndr.internal.Driver
import java.nio.ByteBuffer

private val logger = KotlinLogging.logger {}

class IndexBufferGL3(val buffer: Int, override val indexCount: Int, override val type: IndexType) : IndexBuffer {

    private var isDestroyed = false

    companion object {
        fun create(elementCount: Int, type: IndexType): IndexBufferGL3 {
            val cb = glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING)
            val buffer = glGenBuffers()
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, buffer)
            checkGLErrors()
            val sizeInBytes = type.sizeInBytes * elementCount
            val useBufferStorage =
                (Driver.instance as DriverGL3).version >= DriverVersionGL.GL_VERSION_4_4 && Driver.glVersion.type == DriverTypeGL.GL
            if (useBufferStorage) {
                glBufferStorage(GL_ELEMENT_ARRAY_BUFFER, sizeInBytes.toLong(), GL_DYNAMIC_STORAGE_BIT)
            } else {
                glBufferData(GL_ELEMENT_ARRAY_BUFFER, sizeInBytes.toLong(), GL_DYNAMIC_DRAW)
            }
            checkGLErrors()
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, cb)
            return IndexBufferGL3(buffer, elementCount, type)
        }
    }

    fun bind() {
        if (isDestroyed) {
            throw IllegalStateException("buffer is destroyed")
        }
        logger.trace { "binding vertex buffer $buffer" }
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, buffer)
        debugGLErrors()
    }

    fun unbind() {
        logger.trace { "unbinding vertex buffer" }
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0)
        debugGLErrors()
    }

    override fun write(data: ByteBuffer, offset: Int) {
        bound {
            glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, offset.toLong(), data)
        }
        checkGLErrors()
    }

    override fun read(data: ByteBuffer, offset: Int) {
        if (isDestroyed) {
            throw IllegalStateException("buffer is destroyed")
        }

        if (data.isDirect) {
            bound {
                glGetBufferSubData(GL_ELEMENT_ARRAY_BUFFER, offset.toLong(), data)
            }
            checkGLErrors()
        } else {
            val temp = BufferUtils.createByteBuffer(data.capacity())
            read(temp, offset)
            data.put(temp)
        }
    }

    override fun destroy() {
        glDeleteBuffers(buffer)
        isDestroyed = true
    }

    private fun bound(f: IndexBufferGL3.() -> Unit) {
        bind()
        this.f()
        unbind()
    }

    override fun close() {
        destroy()
    }
}

