package org.openrndr.draw

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Represents a single element within a vertex, describing the structure of vertex data in a graphics pipeline.
 *
 * @property attribute The name of the attribute this element corresponds to (e.g., position, color, normal).
 * @property offset The byte offset of this element within a vertex structure.
 * @property type The data type of this vertex element, represented by [VertexElementType].
 * @property arraySize The number of elements in this vertex attribute array; typically 1 for non-array attributes.
 */
data class VertexElement(val attribute: String, val offset: Int, val type: VertexElementType, val arraySize: Int)

/**
 * VertexBuffer Layout describes how data is organized in the VertexBuffer
 */
class VertexFormat {

    var items: MutableList<VertexElement> = mutableListOf()
    private var vertexSize = 0

    /**
     * The size of the [VertexFormat] in bytes
     */
    val size get() = vertexSize

    /**
     * Appends a position component to the layout
     * @param dimensions
     */
    fun position(dimensions: Int) = attribute("position", floatTypeFromDimensions(dimensions))

    /**
     * Insert padding in the layout
     * @param paddingInBytes the amount of padding in bytes
     */
    fun padding(paddingInBytes: Int) = attribute("_", VertexElementType.UINT8, paddingInBytes)

    private fun floatTypeFromDimensions(dimensions: Int): VertexElementType {
        return when (dimensions) {
            1 -> VertexElementType.FLOAT32
            2 -> VertexElementType.VECTOR2_FLOAT32
            3 -> VertexElementType.VECTOR3_FLOAT32
            4 -> VertexElementType.VECTOR4_FLOAT32
            else -> throw IllegalArgumentException("dimensions can only be 1, 2, 3 or 4 (got $dimensions)")
        }
    }

    /**
     * Appends a normal component to the layout
     * @param dimensions the number of dimensions of the normal vector
     */
    fun normal(dimensions: Int) = attribute("normal", floatTypeFromDimensions(dimensions))

    /**
     * Appends a color attribute to the layout
     * @param dimensions
     */
    fun color(dimensions: Int) = attribute("color", floatTypeFromDimensions(dimensions))

    fun textureCoordinate(dimensions: Int = 2, index: Int = 0) = attribute("texCoord$index", floatTypeFromDimensions(dimensions))


    /**
     * Adds a custom attribute to the [VertexFormat]
     */
    fun attribute(name: String, type: VertexElementType, arraySize: Int = 1) {
        val offset = items.sumOf { it.arraySize * it.type.sizeInBytes }
        val item = VertexElement(name, offset, type, arraySize)
        items.add(item)
        vertexSize += type.sizeInBytes * arraySize
    }

    override fun toString(): String {
        return "VertexFormat{" +
                "items=" + items +
                ", vertexSize=" + vertexSize +
                '}'
    }

    fun hasAttribute(name: String): Boolean = items.any { it.attribute == name }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VertexFormat) return false


        return items == other.items
    }

    override fun hashCode(): Int {
        return items.hashCode()
    }


}

/**
 * Build a vertex format
 */
@OptIn(ExperimentalContracts::class)
fun vertexFormat(builder: VertexFormat.() -> Unit): VertexFormat {
    contract {
        callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
    }

    return VertexFormat().apply { builder() }
}
