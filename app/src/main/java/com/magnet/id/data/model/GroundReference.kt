package com.magnet.id.data.model

/**
 * Stores in-plane magnetic offsets used to align the stick's 3D orientation
 * so that resting the phone or stick flat on the ground/surface establishes
 * a perfectly perpendicular 90° upright baseline with zero lean.
 */
data class GroundReference(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val isGrounded: Boolean = false
) {
    companion object {
        val ZERO = GroundReference(0f, 0f, false)
    }
}
