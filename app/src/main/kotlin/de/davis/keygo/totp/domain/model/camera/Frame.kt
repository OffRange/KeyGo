package de.davis.keygo.totp.domain.model.camera

data class Frame(
    val dataNv21: ByteArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Frame

        if (width != other.width) return false
        if (height != other.height) return false
        if (rotationDegrees != other.rotationDegrees) return false
        if (!dataNv21.contentEquals(other.dataNv21)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + rotationDegrees
        result = 31 * result + dataNv21.contentHashCode()
        return result
    }
}