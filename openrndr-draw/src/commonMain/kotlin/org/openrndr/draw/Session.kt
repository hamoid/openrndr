package org.openrndr.draw

import org.openrndr.internal.Driver

private val sessionStack = mutableMapOf<Long, ArrayDeque<Session>>()

/**
 * Session statistics
 */
class SessionStatistics(
        val renderTargets: Int,
        val colorBuffers: Int,
        val depthBuffers: Int,
        val bufferTextures: Int,
        val indexBuffers: Int,
        val vertexBuffers: Int,
        val shaders: Int,
        val cubemaps: Int,
        val arrayTextures: Int,
        val computeShaders: Int,
        val atomicCounterBuffers: Int,
        val arrayCubemaps: Int,
        val shaderStorageBuffers: Int)

/**
 * Represents a GPU resource management session. A session is responsible for tracking and managing
 * GPU resources in a hierarchical manner. Sessions can be forked, creating child sessions, and
 * ended, destroying all GPU resources associated with the session.
 *
 * @property parent The parent session, or null if this is a root session.
 */
class Session(val parent: Session?) {
    val context = Driver.instance.contextID

    companion object {
        /**
         * The session stack (on the active context)
         */
        val stack: ArrayDeque<Session>
            get() = sessionStack.getOrPut(Driver.instance.contextID) { ArrayDeque<Session>().apply { addLast(Session(null)) } }

        /**
         * The active session (on the active context)
         */
        val active: Session
            get() = stack.last()

        /**
         * The root session (on the active context)
         */
        val root: Session
            get() = stack.first()

        /**
         * Ends the active session and pops it off the session stack (on the active context)
         */
        fun endActive() {
            val session = sessionStack.getValue(Driver.instance.contextID).removeLast()
            session.end()
        }
    }

    private val children = mutableListOf<Session>()

    private val renderTargets = mutableSetOf<RenderTarget>()
    private val colorBuffers = mutableSetOf<ColorBuffer>()
    private val depthBuffers = mutableSetOf<DepthBuffer>()
    private val bufferTextures = mutableSetOf<BufferTexture>()
    private val vertexBuffers = mutableSetOf<VertexBuffer>()
    private val shaders = mutableSetOf<Shader>()
    private val computeShaders = mutableSetOf<ComputeShader>()
    private val cubemaps = mutableSetOf<Cubemap>()
    private val arrayTextures = mutableSetOf<ArrayTexture>()
    private val arrayCubemaps = mutableSetOf<ArrayCubemap>()
    private val indexBuffers = mutableSetOf<IndexBuffer>()
    private val volumeTextures = mutableSetOf<VolumeTexture>()
    private val shaderStorageBuffers = mutableSetOf<ShaderStorageBuffer>()
    private val atomicCounterBuffers = mutableSetOf<AtomicCounterBuffer>()

    /** Session statistics */
    val statistics
        get() =
            SessionStatistics(renderTargets = renderTargets.size,
                    colorBuffers = colorBuffers.size,
                    depthBuffers = depthBuffers.size,
                    bufferTextures = bufferTextures.size,
                    indexBuffers = indexBuffers.size,
                    vertexBuffers = vertexBuffers.size,
                    shaders = shaders.size,
                    cubemaps = cubemaps.size,
                    arrayTextures = arrayTextures.size,
                    computeShaders = computeShaders.size,
                    atomicCounterBuffers = atomicCounterBuffers.size,
                    arrayCubemaps = arrayCubemaps.size,
                    shaderStorageBuffers = shaderStorageBuffers.size)

    fun track(renderTarget: RenderTarget) = renderTargets.add(renderTarget)
    fun untrack(renderTarget: RenderTarget) = renderTargets.remove(renderTarget)

    fun track(colorBuffer: ColorBuffer) = colorBuffers.add(colorBuffer)
    fun untrack(colorBuffer: ColorBuffer) = colorBuffers.remove(colorBuffer)

    fun track(depthBuffer: DepthBuffer) = depthBuffers.add(depthBuffer)
    fun untrack(depthBuffer: DepthBuffer) = depthBuffers.remove(depthBuffer)

    fun track(vertexBuffer: VertexBuffer) = vertexBuffers.add(vertexBuffer)
    fun untrack(vertexBuffer: VertexBuffer) = vertexBuffers.remove(vertexBuffer)

    fun track(indexBuffer: IndexBuffer) = indexBuffers.add(indexBuffer)
    fun untrack(indexBuffer: IndexBuffer) = indexBuffers.remove(indexBuffer)

    fun track(shader: Shader) = shaders.add(shader)
    fun untrack(shader: Shader) = shaders.remove(shader)

    fun track(computeShader: ComputeShader) = computeShaders.add(computeShader)
    fun untrack(computeShader: ComputeShader) = computeShaders.remove(computeShader)

    fun track(cubemap: Cubemap) = cubemaps.add(cubemap)
    fun untrack(cubemap: Cubemap) = cubemaps.remove(cubemap)

    fun track(bufferTexture: BufferTexture) = bufferTextures.add(bufferTexture)
    fun untrack(bufferTexture: BufferTexture) = bufferTextures.remove(bufferTexture)

    fun track(arrayTexture: ArrayTexture) = arrayTextures.add(arrayTexture)
    fun untrack(arrayTexture: ArrayTexture) = arrayTextures.remove(arrayTexture)

    fun track(atomicCounterBuffer: AtomicCounterBuffer) = atomicCounterBuffers.add(atomicCounterBuffer)
    fun untrack(atomicCounterBuffer: AtomicCounterBuffer) = atomicCounterBuffers.remove(atomicCounterBuffer)

    fun track(arrayCubemap: ArrayCubemap) = arrayCubemaps.add(arrayCubemap)
    fun untrack(arrayCubemap: ArrayCubemap) = arrayCubemaps.remove(arrayCubemap)

    fun track(volumeTexture: VolumeTexture) = volumeTextures.add(volumeTexture)
    fun untrack(volumeTexture: VolumeTexture) = volumeTextures.remove(volumeTexture)

    fun track(shaderStorageBuffer: ShaderStorageBuffer) = shaderStorageBuffers.add(shaderStorageBuffer)
    fun untrack(shaderStorageBuffer: ShaderStorageBuffer) = shaderStorageBuffers.remove(shaderStorageBuffer)

    /**
     * Fork the session
     */
    fun fork(): Session {
        val child = Session(this)
        sessionStack.getValue(Driver.instance.contextID).addLast(child)
        children.add(child)
        return child
    }

    /**
     * Ends the session, destroys any GPU resources in use by the session
     */
    fun end() {
        require(Driver.instance.contextID == context)

        parent?.children?.remove(this)

        for (child in children.map { it }) {
            child.end()
        }
        children.clear()

        renderTargets.map { it }.forEach {
            it.detachColorAttachments()
            it.detachDepthBuffer()
            it.destroy()
        }
        renderTargets.clear()

        colorBuffers.map { it }.forEach {
            it.destroy()
        }
        colorBuffers.clear()

        depthBuffers.map { it }.forEach {
            it.destroy()
        }
        depthBuffers.clear()

        vertexBuffers.map { it }.forEach {
            it.destroy()
        }
        vertexBuffers.clear()

        indexBuffers.map { it }.forEach {
            it.destroy()
        }
        indexBuffers.clear()

        cubemaps.map { it }.forEach {
            it.destroy()
        }
        cubemaps.clear()

        bufferTextures.map { it }.forEach {
            it.destroy()
        }
        bufferTextures.clear()

        shaders.map { it }.forEach {
            it.destroy()
        }
        shaders.clear()

        computeShaders.map { it }.forEach {
            it.destroy()
        }
        computeShaders.clear()

        arrayTextures.map { it }.forEach {
            it.destroy()
        }
        arrayTextures.clear()

        arrayCubemaps.map { it }.forEach {
            it.destroy()
        }
        arrayCubemaps.clear()


        shaderStorageBuffers.map { it }.forEach {
            it.destroy()
        }
        shaderStorageBuffers.clear()
    }
}

/** Runs code inside a (short-lived) session */
fun session(code: () -> Unit) {
    val s = Session.active.fork()
    code()
    s.end()
}

/**
 * Mark a GPU resource or code that uses GPU resources as persistent
 */
fun <T> persistent(builder: () -> T): T {
    Session.stack.addLast(Session.root)
    val result = builder()
    Session.stack.removeLast()
    return result
}