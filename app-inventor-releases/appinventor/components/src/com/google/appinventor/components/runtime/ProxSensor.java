// Copyright 2008 Google Inc. All Rights Reserved.


package com.google.appinventor.components.runtime;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.YaVersion;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Physical world component that can detect shaking and measure
 * acceleration in three dimensions.  It is implemented using
 * android.hardware.SensorListener
 * (http://developer.android.com/reference/android/hardware/SensorListener.html).
 *
 * <p>From the Android documentation:
 * "Sensor values are acceleration in the X, Y and Z axis, where the X axis
 * has positive direction toward the right side of the device, the Y axis has
 * positive direction toward the top of the device and the Z axis has
 * positive direction toward the front of the device. The direction of the
 * force of gravity is indicated by acceleration values in the X, Y and Z
 * axes. The typical case where the device is flat relative to the surface of
 * the Earth appears as -STANDARD_GRAVITY in the Z axis and X and Y values
 * close to zero. Acceleration values are given in SI units (m/s^2)."
 *
 */
// TODO(user): ideas - event for knocking
@DesignerComponent(version = YaVersion.PROXSENSOR_COMPONENT_VERSION,
    description = "<p>Non-visible component that can detect shaking and " +
    "measure acceleration approximately in three dimensions using SI units " +
    "(m/s<sup>2</sup>).  The components are: <ul>" +
    "<li> <strong>xAccel</strong>: 0 when the phone is at rest on a flat " +
    "     surface, positive when the phone is tilted to the right (i.e., " +
    "     its left side is raised), and negative when the phone is tilted " +
    "     to the left (i.e., its right size is raised).</li> " +
    "<li> <strong>yAccel</strong>: 0 when the phone is at rest on a flat " +
    "     surface, positive when its bottom is raised, and negative when " +
    "     its top is raised. </li> " +
    "<li> <strong>zAccel</strong>: Equal to -9.8 (earth's gravity in meters per " +
    "     second per second when the device is at rest parallel to the ground " +
    "     with the display facing up, " +
    "     0 when perpindicular to the ground, and +9.8 when facing down.  " +
    "     The value can also be affected by accelerating it with or against " +
    "     gravity. </li></ul></p> ",
    category = ComponentCategory.SENSORS,
    nonVisible = true,
    iconName = "images/proxsensor.png")
@SimpleObject
public class ProxSensor extends AndroidNonvisibleComponent
    implements OnStopListener, OnResumeListener, SensorComponent, SensorEventListener, Deleteable {

  // Shake threshold - derived by trial
  private static final double SHAKE_THRESHOLD = 8.0;

  // Cache for shake detection
  private static final int SENSOR_CACHE_SIZE = 10;
  private final Queue<Float> X_CACHE = new LinkedList<Float>();
  private final Queue<Float> Y_CACHE = new LinkedList<Float>();
  private final Queue<Float> Z_CACHE = new LinkedList<Float>();

  private final Queue<Float> D_CACHE = new LinkedList<Float>();

  // Backing for sensor values
  private float xAccel;
  private float yAccel;
  private float zAccel;

  private float dValue;

  private int accuracy;

  // Sensor manager
  private final SensorManager sensorManager;

  // Indicates whether the accelerometer should generate events
  private boolean enabled;

  private Sensor proxSensor;

  /**
   * Creates a new AccelerometerSensor component.
   *
   * @param container  ignored (because this is a non-visible component)
   */
  public ProxSensor(ComponentContainer container) {
    super(container.$form());
    form.registerForOnResume(this);
    form.registerForOnStop(this);

    enabled = true;
    sensorManager = (SensorManager) container.$context().getSystemService(Context.SENSOR_SERVICE);
    proxSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
    startListening();
  }

  /**
   * Indicates the acceleration changed in the X, Y, and/or Z dimensions.
   */
  @SimpleEvent
  public void ProxChanged(float dValue) {

    this.dValue = dValue;

    addToSensorCache(D_CACHE, dValue);

    EventDispatcher.dispatchEvent(this, "ProxChanged", dValue);
  }

  /**
   * Indicates the device started being shaken or continues to be shaken.
   */
  @SimpleEvent
  public void Shaking() {
    EventDispatcher.dispatchEvent(this, "Shaking");
  }

  /**
   * Available property getter method (read-only property).
   *
   * @return {@code true} indicates that an accelerometer sensor is available,
   *         {@code false} that it isn't
   */
  @SimpleProperty(
      category = PropertyCategory.BEHAVIOR)
  public boolean Available() {
    List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_PROXIMITY);
    return (sensors.size() > 0);
  }

  /**
   * If true, the sensor will generate events.  Otherwise, no events
   * are generated even if the device is accelerated or shaken.
   *
   * @return {@code true} indicates that the sensor generates events,
   *         {@code false} that it doesn't
   */
  @SimpleProperty(
      category = PropertyCategory.BEHAVIOR)
  public boolean Enabled() {
    return enabled;
  }

  // Assumes that sensorManager has been initialized, which happens in constructor
  private void startListening() {
    sensorManager.registerListener(this, proxSensor, SensorManager.SENSOR_DELAY_GAME);
  }

  // Assumes that sensorManager has been initialized, which happens in constructor
  private void stopListening() {
    sensorManager.unregisterListener(this);
  }

  /**
   * Specifies whether the sensor should generate events.  If true,
   * the sensor will generate events.  Otherwise, no events are
   * generated even if the device is accelerated or shaken.
   *
   * @param enabled  {@code true} enables sensor event generation,
   *                 {@code false} disables it
   */
  @DesignerProperty(editorType = DesignerProperty.PROPERTY_TYPE_BOOLEAN,
      defaultValue = "True")
  @SimpleProperty
  public void Enabled(boolean enabled) {
    if (this.enabled == enabled) {
      return;
    }
    this.enabled = enabled;
    if (enabled) {
      startListening();
    } else {
      stopListening();
    }
  }

  /**
   * Returns the acceleration in the X-dimension in SI units (m/s^2).
   * The sensor must be enabled to return meaningful values.
   *
   * @return  X acceleration
   */
  @SimpleProperty(
      category = PropertyCategory.BEHAVIOR)
  public float dValue() {
    return dValue;
  }

  /*
   * Updating sensor cache, replacing oldest values.
   */
  private void addToSensorCache(Queue<Float> cache, float value) {
    if (cache.size() >= SENSOR_CACHE_SIZE) {
      cache.remove();
    }
    cache.add(value);
  }

  /*
   * Indicates whether there was a sudden, unusual movement.
   */
  // TODO(user): Maybe this can be improved.
  // See http://www.utdallas.edu/~rxb023100/pubs/Accelerometer_WBSN.pdf.
  private boolean isShaking(Queue<Float> cache, float currentValue) {
    float average = 0;
    for (float value : cache) {
      average += value;
    }

    average /= cache.size();

    return Math.abs(average - currentValue) > SHAKE_THRESHOLD;
  }

  // SensorListener implementation
  @Override
  public void onSensorChanged(SensorEvent sensorEvent) {
    if (enabled) {
      final float[] values = sensorEvent.values;
      dValue = values[0];
      ProxChanged(dValue);
    }
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    // TODO(markf): Figure out if we actually need to do something here.
  }

  // OnResumeListener implementation

  @Override
  public void onResume() {
    if (enabled) {
      startListening();
    }
  }

  // OnStopListener implementation

  @Override
  public void onStop() {
    if (enabled) {
      stopListening();
    }
  }

  // Deleteable implementation

  @Override
  public void onDelete() {
    if (enabled) {
      stopListening();
    }
  }
}
