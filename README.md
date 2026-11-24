# Magnin (Magnetic Input)

Transform raw smartphone magnetometer sensor data into precise kinematic inputs. By tracking the spatial magnetic flux dipole of an external magnet, **Magnin** enables rich physical interactions—including multi-axis 3D tilt, continuous rotary dials, pitch levers, and 8-way gated joystick navigation.

---

## Features

- **High-Performance Magnetometer Tracking**: Live 60fps sampling with hardware sensor calibration and noise filtering.
- **Kinematic Constraints**:
  - `FREE`: Unconstrained 2-DOF tilt elevation and heading tracking.
  - `DIAL`: Precession angle resolver for rotary knobs and scrubbers (1-DOF).
  - `LEVER`: Pitch angle throttle / slider (1-DOF).
  - `JOYSTICK`: 8-way directional gating with deadzone hysteresis.
- **Reference Playground Views**:
  - **Iron Filings**: Microscopic needle vector field simulation.
  - **Flux Ripples**: Dynamic radial wave propagation reflecting real-time field intensity and polarity (North Cyan / South Red).
  - **3D Gimbal**: Physical 3-axis rotational gimbal visualizer.
  - **Dial**: Clean blank custom canvas reserved for modular magnetic input widgets.
- **Ground Orientation Calibration (`[GND]`)**: Reference zeroing so resting the phone or stick flat establishes a perpendicular upright baseline.
- **Hardware Sweet-Spot Alignment (`[SNAP]`)**: Dynamic reticle alignment to the physical magnetometer chip coordinates.

---

## Project Structure

```
├── app/                  # Showcase demo application & visualizers
│   └── src/main/java/com/magnet/id/
│       ├── data/         # Magnetic models, kinematic state, and calibrations
│       ├── sensor/       # Real-time Hardware SensorManager listener
│       └── ui/           # Jetpack Compose visualizers, HUD, and Dial page
└── ...
```

---

## Getting Started

1. Clone the repository:
   ```bash
   gh repo clone PrashantNaikHC/MagnIn
   ```
2. Open the project in Android Studio (Koala / Ladybug or newer with JDK 17).
3. Connect an Android device with a built-in magnetometer (compass sensor) and run:
   ```bash
   ./gradlew installDebug
   ```

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
