package org.myrobotlab.service;

import java.util.ArrayList;
import java.util.List;

import org.myrobotlab.framework.Service;
import org.myrobotlab.kinematics.Point;
import org.myrobotlab.logging.Level;
import org.myrobotlab.logging.LoggerFactory;
import org.myrobotlab.logging.LoggingFactory;
import org.myrobotlab.service.data.LeapData;
import org.myrobotlab.service.data.LeapHand;
import org.myrobotlab.service.interfaces.LeapDataListener;
import org.myrobotlab.service.interfaces.LeapDataPublisher;
import org.myrobotlab.service.interfaces.PointPublisher;
import org.slf4j.Logger;

import com.leapmotion.leap.Controller;
import com.leapmotion.leap.Finger;
import com.leapmotion.leap.Frame;
import com.leapmotion.leap.Gesture;
import com.leapmotion.leap.Hand;
import com.leapmotion.leap.Vector;

public class LeapMotion extends Service implements LeapDataListener, LeapDataPublisher, PointPublisher {

  private static final long serialVersionUID = 1L;

  public final static Logger log = LoggerFactory.getLogger(LeapMotion.class);

  final transient LeapMotionListener poller;

  transient Controller controller = null;

  public LeapData lastLeapData = null;

  int numFrames = 0;

  public LeapMotion(String n, String id) {
    super(n, id);
    poller = new LeapMotionListener(this);
  }

  public void activateDefaultMode() {
    controller.setPolicyFlags(Controller.PolicyFlag.POLICY_DEFAULT);
    log.info("default mode active");
    return;
  }

  /**
   * Requesting the Optimize for HMD policy tunes the hand recognition logic to
   * better recognize hands when the Leap Motion device is attached to a
   * head-mounted display. This primarily improves the classification of hands
   * as left versus right and the direction of the palm. This policy does not
   * improve tracking when the Leap Motion is mounted in a fixed, downward
   * orientation.
   */
  public void activateVRMode() {
    controller.setPolicyFlags(Controller.PolicyFlag.POLICY_OPTIMIZE_HMD);
    log.info("virtual reality mode active");
    return;
  }

  public void addFrameListener(Service service) {
    addListener("publishFrame", service.getName(), "onFrame");
  }

  public void addLeapDataListener(Service service) {
    addListener("publishLeapData", service.getName(), "onLeapData");
  }

  public void checkPolicy() {
    log.info("controller.policyFlags()");
  }

  /**
   * Return the angle of the finger for the hand specified This computes the
   * angle based on the dot product of the palmNormal and the fingerDirection
   * Theta = arccos( (V1.V2) / ( |V1| * |V2| )
   * 
   * @param hand
   *          - "left" or "right"
   * @param tip
   *          - 0 (thumb) / 1 (index) .. etc..
   * @return angle in degrees
   */
  public double getJointAngle(String hand, Integer tip) {
    com.leapmotion.leap.Hand h = null;
    if ("left".equalsIgnoreCase(hand)) {
      // left hand
      h = controller.frame().hands().leftmost();
    } else {
      // right hand
      h = controller.frame().hands().rightmost();
    }
    // TODO: does this return the correct finger?
    Finger f = h.fingers().get(tip);
    Vector palmNormal = h.palmNormal();
    Vector fDir = f.direction();
    // TODO: validate that this is what we actually want.
    // otherwise we can directly compute the angleTo in java.
    float angleInRadians = palmNormal.angleTo(fDir);
    // convert to degrees so it's easy to pass to servos
    double angle = Math.toDegrees(angleInRadians);
    return angle;
  }

  public float getLeftStrength() {
    Frame frame = controller.frame();
    com.leapmotion.leap.Hand hand = frame.hands().leftmost();
    float strength = hand.grabStrength();
    return strength;
  }

  public float getRightStrength() {
    Frame frame = controller.frame();
    com.leapmotion.leap.Hand hand = frame.hands().rightmost();
    float strength = hand.grabStrength();
    return strength;
  }

  @Override
  public LeapData onLeapData(LeapData data) {

    return data;
    // TODO Auto-generated method stub

  }

  public Frame publishFrame(Frame frame) {
    return frame;
  }

  public Controller publishInit(Controller controller) {
    return controller;
  }

  @Override
  public LeapData publishLeapData(LeapData data) {
    // if (data != null) {
    // log.info("DATA" + data.leftHand.posX);
    // }
    return data;
  }

  public void releaseService() {
    poller.stop();
    super.releaseService();    
  }
  
  @Override
  public void startService() {
    super.startService();
    if (controller == null) {
      controller = new Controller();
    }
    // we've been asked to start.. we should start tracking !
    this.startTracking();
  }

  public void startTracking() {
    poller.start();
  }

  public void stopTracking() {
    poller.stop();
  }

  @Override
  public List<Point> publishPoints(List<Point> points) {
    return points;
  }

  public void addPointsListener(Service s) {
    // TODO - reflect on a public heard method - if doesn't exist error ?
    addListener("publishPoints", s.getName(), "onPoints");
  }

  private double computeAngleDegrees(Finger f, Vector palmNormal) {
    Vector fDir = f.direction();
    // TODO: validate that this is what we actually want.
    // otherwise we can directly compute the angleTo in java.
    double angleInRadians = palmNormal.angleTo(fDir);
    // convert to degrees so it's easy to pass to servos
    double angle = Math.toDegrees(angleInRadians);
    return angle;
  }

  private LeapHand mapLeapHandData(Hand lh) {
    LeapHand mrlHand = new LeapHand();
    // process the normal
    Vector palmNormal = lh.palmNormal();
    mrlHand.palmNormalX = palmNormal.getX();
    mrlHand.palmNormalY = palmNormal.getY();
    mrlHand.palmNormalZ = palmNormal.getZ();

    mrlHand.posX = lh.arm().center().getX();
    mrlHand.posY = lh.arm().center().getY();
    mrlHand.posZ = lh.arm().center().getZ();

    // handle the fingers.
    for (Finger.Type t : Finger.Type.values()) {
      Finger f = lh.fingers().get(t.ordinal());
      int angle = (int) computeAngleDegrees(f, palmNormal);
      if (t.equals(Finger.Type.TYPE_INDEX))
        mrlHand.index = angle;
      else if (t.equals(Finger.Type.TYPE_MIDDLE))
        mrlHand.middle = angle;
      else if (t.equals(Finger.Type.TYPE_RING))
        mrlHand.ring = angle;
      else if (t.equals(Finger.Type.TYPE_PINKY))
        mrlHand.pinky = angle;
      else if (t.equals(Finger.Type.TYPE_THUMB))
        mrlHand.thumb = angle;
      else
        log.warn("Unknown finger! eek..");
    }
    return mrlHand;
  }

  public void onFrame(Frame frame) {
    LeapData data = new LeapData();
    
    // The old publishFrame method for those who want it.
    data.frame = frame;
    invoke("publishFrame", data.frame);
    // grab left/right hands

    Hand lh = frame.hands().leftmost();
    Hand rh = frame.hands().rightmost();
    // map the data to the MRL Hand pojo

    LeapHand mrlLHand = null;
    LeapHand mrlRHand = null;
    if (lh.isLeft()) {
      mrlLHand = mapLeapHandData(lh);
    }
    if (rh.isRight()) {
      mrlRHand = mapLeapHandData(rh);
    }
    // set them to the LeapData obj
    data.leftHand = mrlLHand;
    data.rightHand = mrlRHand;
    // Grab the current frame
    // Track the last valid data frame.
    // TODO: test and make sure this is worky?
    if (data.frame.isValid()) {
      numFrames++;
      lastLeapData = data;
      // only publish valid frames ?
      invoke("publishLeapData", data);

      ArrayList<Point> points = new ArrayList<Point>();
      for (Hand h : data.frame.hands()) {
        // position information
        double x = h.arm().center().getX();
        double y = h.arm().center().getY();
        double z = h.arm().center().getZ();
        // orientation information
        double roll = h.palmNormal().roll();
        double pitch = h.palmNormal().pitch();
        double yaw = h.palmNormal().yaw();
        // create the point to publish
        Point palmPoint = new Point(x, y, z, roll, pitch, yaw);

        // add it to the list of points we publish
        points.add(palmPoint);
      }
      // publish the points.
      if (points.size() > 0) {
        // TODO: gotta down sample for ik3d to keep up.
        if (numFrames % 20 == 0) {
          invoke("publishPoints", points);
        }
      }
    }
  }

  ///////////// thread management begin

  public class LeapMotionListener implements Runnable {

    transient LeapMotion myService = null;
    transient Thread myThread = null;
    transient Object lock = new Object();

    public LeapMotionListener(LeapMotion myService) {
      this.myService = myService;
    }

    boolean running = false;
    boolean firstTimeConnected = true;

    @Override
    public void run() {
      running = true;
      try {
        while (running) {
          if (controller.isConnected()) {
            if(firstTimeConnected) {
              controller.enableGesture(Gesture.Type.TYPE_SWIPE);
              controller.enableGesture(Gesture.Type.TYPE_CIRCLE);
              controller.enableGesture(Gesture.Type.TYPE_SCREEN_TAP);
              controller.enableGesture(Gesture.Type.TYPE_KEY_TAP);
              firstTimeConnected = false;
            }
            onFrame(controller.frame());
            // FIXME - running average or other filter
            // TODO - optimization publish on change
            Service.sleep(50);            
          } else {
            log.info("controller not connected");
            firstTimeConnected = true;
            Service.sleep(300);
          }
        }
      } catch (Exception e) {
        log.error("listening threw", e);
      }

      running = false;
      myThread = null;
    }

    public void start() {
      synchronized (lock) {
        if (myThread == null) {
          myThread = new Thread(this, myService.getName() + "-listener");
          myThread.start();
        } else {
          log.info(myService.getName() + "-listener already started");
        }
      }
    }

    public void stop() {
      synchronized (lock) {
        running = false;
        myThread = null;
        Service.sleep(30);
      }
    }
  }
  /////////// thread management end

  public static void main(String[] args) {
    LoggingFactory.init(Level.INFO);
    try {

      // leap.startService();
      Runtime.start("webgui", "WebGui");
      Runtime.start("i01.leap", "LeapMotion");
      Runtime.start("intro", "Intro");
      Runtime.start("i01", "InMoov2");

      // Have the sample listener receive events from the controller

      // Remove the sample listener when done
    } catch (Exception e) {
      log.error("main threw", e);
    }
  }

}
