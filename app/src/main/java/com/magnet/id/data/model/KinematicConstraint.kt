package com.magnet.id.data.model

/**
 * Kinematic constraints applied to the 3D stick model in OBJECT_3D mode
 * to filter orthogonal hand noise, reject tremor, and provide specialized interactions.
 */
enum class KinematicConstraint {
    /** Full unconstrained 2-DoF conical tilt and clock precession. */
    FREE,

    /** 1-DoF pure rotary dial motion: tilt angle is locked, tracking clock precession with 0 radial jitter. */
    DIAL,

    /** 1-DoF pitch lever / throttle motion: azimuth is locked, tracking vertical-to-horizontal tilt with 0 lateral wobble. */
    LEVER,

    /** 2-DoF gated joystick: center deadzone (< 8°) snaps upright, with 8-way soft directional detents. */
    JOYSTICK
}
