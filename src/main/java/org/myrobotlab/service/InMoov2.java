package org.myrobotlab.service;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.io.FilenameUtils;
import org.myrobotlab.framework.Plan;
import org.myrobotlab.framework.Platform;
import org.myrobotlab.framework.Registration;
import org.myrobotlab.framework.Service;
import org.myrobotlab.framework.Status;
import org.myrobotlab.framework.interfaces.Attachable;
import org.myrobotlab.framework.interfaces.ServiceInterface;
import org.myrobotlab.inmoov.Vision;
import org.myrobotlab.io.FileIO;
import org.myrobotlab.logging.Level;
import org.myrobotlab.logging.LoggerFactory;
import org.myrobotlab.logging.LoggingFactory;
import org.myrobotlab.opencv.OpenCVData;
import org.myrobotlab.service.config.InMoov2Config;
import org.myrobotlab.service.config.ServiceConfig;
import org.myrobotlab.service.config.WebGuiConfig;
import org.myrobotlab.service.data.JoystickData;
import org.myrobotlab.service.data.Locale;
import org.myrobotlab.service.interfaces.IKJointAngleListener;
import org.myrobotlab.service.interfaces.JoystickListener;
import org.myrobotlab.service.interfaces.LocaleProvider;
import org.myrobotlab.service.interfaces.ServiceLifeCycleListener;
import org.myrobotlab.service.interfaces.ServoControl;
import org.myrobotlab.service.interfaces.Simulator;
import org.myrobotlab.service.interfaces.SpeechRecognizer;
import org.myrobotlab.service.interfaces.SpeechSynthesis;
import org.myrobotlab.service.interfaces.TextListener;
import org.myrobotlab.service.interfaces.TextPublisher;
import org.slf4j.Logger;

public class InMoov2 extends Service implements ServiceLifeCycleListener, TextListener, TextPublisher, JoystickListener, LocaleProvider, IKJointAngleListener {

  public final static Logger log = LoggerFactory.getLogger(InMoov2.class);

  public static LinkedHashMap<String, String> lpVars = new LinkedHashMap<String, String>();

  private static final long serialVersionUID = 1L;

  static String speechRecognizer = "WebkitSpeechRecognition";

  /**
   * @param someScriptName
   *          execute a resource script
   * @return success or failure
   */
  public boolean execScript(String someScriptName) {
    try {
      Python p = (Python) Runtime.start("python", "Python");
      String script = getResourceAsString(someScriptName);
      return p.exec(script, true);
    } catch (Exception e) {
      error("unable to execute script %s", someScriptName);
      return false;
    }
  }

  /**
   * Single place for InMoov2 service to execute arbitrary code - needed
   * initially to set "global" vars in python
   * 
   * @param pythonCode
   * @return
   */
  public boolean exec(String pythonCode) {
    try {
      Python p = (Python) Runtime.start("python", "Python");
      return p.exec(pythonCode, true);
    } catch (Exception e) {
      error("unable to execute script %s", pythonCode);
      return false;
    }
  }

  /**
   * Part of service life cycle - a new servo has been started
   */
  public void onStarted(String fullname) {
    log.info("{} started", fullname);
    try {
      ServiceInterface si = Runtime.getService(fullname);
      if ("Servo".equals(si.getSimpleName())) {
        log.info("sending setAutoDisable true to {}", fullname);
        send(fullname, "setAutoDisable", true);
        // ServoControl sc = (ServoControl)Runtime.getService(name);
      }
    } catch (Exception e) {
      log.error("onStarted threw", e);
    }
  }

  public void startService() {
    super.startService();
    Runtime runtime = Runtime.getInstance();
    // FIXME - shouldn't need this anymore
    Runtime.getInstance().attachServiceLifeCycleListener(getName());

    try {
      // copy config if it doesn't already exist
      String resourceBotDir = FileIO.gluePaths(getResourceDir(), "config");
      List<File> files = FileIO.getFileList(resourceBotDir);
      for (File f : files) {
        String botDir = "data/config/" + f.getName();
        File bDir = new File(botDir);
        if (bDir.exists() || !f.isDirectory()) {
          log.info("skipping data/config/{}", botDir);
        } else {
          log.info("will copy new data/config/{}", botDir);
          try {
            FileIO.copy(f.getAbsolutePath(), botDir);
          } catch (Exception e) {
            error(e);
          }
        }
      }

      // copy (if they don't already exist) the chatbots which came with InMoov2
      resourceBotDir = FileIO.gluePaths(getResourceDir(), "chatbot/bots");
      files = FileIO.getFileList(resourceBotDir);
      for (File f : files) {
        String botDir = "data/ProgramAB/" + f.getName();
        if (new File(botDir).exists()) {
          log.info("found data/ProgramAB/{} not copying", botDir);
        } else {
          log.info("will copy new data/ProgramAB/{}", botDir);
          try {
            FileIO.copy(f.getAbsolutePath(), botDir);
          } catch (Exception e) {
            error(e);
          }
        }
      }

    } catch (Exception e) {
      error(e);
    }

    // if (loadGestures) {
    // loadGestures();
    // }

    runtime.invoke("publishConfigList");
  }

  public void onCreated(String fullname) {
    log.info("{} created", fullname);
  }

  /**
   * This method will load a python file into the python interpreter.
   * 
   * @param file
   *          file to load
   * @return success/failure
   */
  @Deprecated /* use execScript - this doesn't handle resources correctly */
  public static boolean loadFile(String file) {
    File f = new File(file);
    Python p = (Python) Runtime.getService("python");
    log.info("Loading  Python file {}", f.getAbsolutePath());
    if (p == null) {
      log.error("Python instance not found");
      return false;
    }

    boolean result = false;
    try {
      // This will open a gazillion tabs in InMoov
      // result = p.execFile(f.getAbsolutePath(), true);

      // old way - not using execFile :(
      String script = FileIO.toString(f.getAbsolutePath());
      result = p.exec(script, true);
    } catch (IOException e) {
      log.error("IO Error loading file : ", e);
      return false;
    }

    if (!result) {
      log.error("Error while loading file {}", f.getAbsolutePath());
      return false;
    } else {
      log.debug("Successfully loaded {}", f.getAbsolutePath());
    }
    return true;
  }

  boolean autoStartBrowser = false;

  transient ProgramAB chatBot;

  transient SpeechRecognizer ear;

  transient OpenCV opencv;

  transient Tracking eyesTracking;

  transient ServoMixer servoMixer;

  transient Python python;

  transient Tracking headTracking;

  transient HtmlFilter htmlFilter;

  transient UltrasonicSensor ultrasonicRight;

  transient UltrasonicSensor ultrasonicLeft;

  transient Pir pir;

  transient ImageDisplay imageDisplay;

  // transient JMonkeyEngine simulator;

  String currentConfigurationName = "default";

  /**
   * supported locales
   */
  Map<String, Locale> locales = null;

  int maxInactivityTimeSeconds = 120;

  transient SpeechSynthesis mouth;

  // FIXME ugh - new MouthControl service that uses AudioFile output
  transient public MouthControl mouthControl;

  boolean mute = false;

  // waiting controable threaded gestures we warn user
  boolean gestureAlreadyStarted = false;
  // FIXME - what the hell is this for ?
  Set<String> gestures = new TreeSet<String>();

  @Deprecated
  public Vision vision;

  // FIXME - remove all direct references
  // transient private HashMap<String, InMoov2Arm> arms = new HashMap<>();

  // protected List<Voice> voices = null;

  protected String voiceSelected;

  transient WebGui webgui;

  protected List<String> configList;

  String lastGestureExecuted;

  Long lastPirActivityTime;

  public InMoov2(String n, String id) {
    super(n, id);

    // InMoov2 has a huge amount of peers

    // by default all servos will auto-disable
    // Servo.setAutoDisableDefault(true); //until peer servo services for
    // InMoov2 have the auto disable behavior, we should keep this

    // same as created in runtime - send asyc message to all
    // registered services, this service has started
    // find all servos - set them all to autoDisable(true)
    // onStarted(name) will handle all future created servos
    List<ServiceInterface> services = Runtime.getServices();
    for (ServiceInterface si : services) {
      if ("Servo".equals(si.getSimpleName())) {
        send(si.getFullName(), "setAutoDisable", true);
      }
    }

    // dynamically gotten from filesystem/bots ?
    locales = Locale.getLocaleMap("en-US", "fr-FR", "es-ES", "de-DE", "nl-NL", "ru-RU", "hi-IN", "it-IT", "fi-FI", "pt-PT", "tr-TR");
    locale = Runtime.getInstance().getLocale();

    // REALLY NEEDS TO BE CLEANED UP - no direct references
    // "publish" scripts which should be executed :(
    // python = (Python) startPeer("python");
    python = (Python) Runtime.start("python", "Python"); // this crud should
                                                         // stop
    // load(locale.getTag()); WTH ?

    // get events of new services and shutdown
    Runtime r = Runtime.getInstance();
    subscribe(r.getName(), "shutdown");
    subscribe(r.getName(), "publishConfigList");

    // FIXME - Framework should auto-magically auto-start peers AFTER
    // construction - unless explicitly told not to
    // peers to start on construction
    // imageDisplay = (ImageDisplay) startPeer("imageDisplay");
  }

  public void addTextListener(TextListener service) {
    // CORRECT WAY ! - no direct reference - just use the name in a subscription
    addListener("publishText", service.getName());
  }

  @Override
  public void attachTextListener(TextListener service) {
    attachTextListener(service.getName());
  }

  /**
   * comes in from runtime which owns the config list
   * 
   * @param configList
   *          list of configs
   */
  public void onConfigList(List<String> configList) {
    this.configList = configList;
    invoke("publishConfigList");
  }

  /**
   * "re"-publishing runtime config list, because I don't want to fix the js
   * subscribeTo :P
   * 
   * @return list of config names
   */
  public List<String> publishConfigList() {
    return configList;
  }

  public void attachTextPublisher(String name) {
    subscribe(name, "publishText");
  }

  @Override
  public void attachTextPublisher(TextPublisher service) {
    subscribe(service.getName(), "publishText");
  }

  public void beginCheckingOnInactivity() {
    beginCheckingOnInactivity(maxInactivityTimeSeconds);
  }

  public void beginCheckingOnInactivity(int maxInactivityTimeSeconds) {
    this.maxInactivityTimeSeconds = maxInactivityTimeSeconds;
    // speakBlocking("power down after %s seconds inactivity is on",
    // this.maxInactivityTimeSeconds);
    log.info("power down after %s seconds inactivity is on", this.maxInactivityTimeSeconds);
    addTask("checkInactivity", 5 * 1000, 0, "checkInactivity");
  }

  public long checkInactivity() {
    // speakBlocking("checking");
    long lastActivityTime = getLastActivityTime();
    long now = System.currentTimeMillis();
    long inactivitySeconds = (now - lastActivityTime) / 1000;
    if (inactivitySeconds > maxInactivityTimeSeconds) {
      // speakBlocking("%d seconds have passed without activity",
      // inactivitySeconds);
      powerDown();
    } else {
      // speakBlocking("%d seconds have passed without activity",
      // inactivitySeconds);
      info("checking checkInactivity - %d seconds have passed without activity", inactivitySeconds);
    }
    return lastActivityTime;
  }

  public void closeAllImages() {
    // imageDisplay.closeAll();
    log.error("implement webgui.closeAllImages");
  }

  public void cycleGestures() {
    // if not loaded load -
    // FIXME - this needs alot of "help" :P
    // WHY IS THIS DONE ?
    if (gestures.size() == 0) {
      loadGestures();
    }

    for (String gesture : gestures) {
      try {
        String methodName = gesture.substring(0, gesture.length() - 3);
        speakBlocking(methodName);
        log.info("executing gesture {}", methodName);
        python.eval(methodName + "()");

        // wait for finish - or timeout ?

      } catch (Exception e) {
        error(e);
      }
    }
  }

  public void disable() {
    sendToPeer("head", "disable");
    sendToPeer("rightHand", "disable");
    sendToPeer("leftHand", "disable");
    sendToPeer("rightArm", "disable");
    sendToPeer("leftArm", "disable");
    sendToPeer("torso", "disable");
  }

  public void displayFullScreen(String src) {
    try {
      if (imageDisplay == null) {
        imageDisplay = (ImageDisplay) startPeer("imageDisplay");
      }
      imageDisplay.displayFullScreen(src);
      log.error("implement webgui.displayFullScreen");
    } catch (Exception e) {
      error("could not display picture %s", src);
    }
  }

  public void enable() {
    sendToPeer("head", "enable");
    sendToPeer("rightHand", "enable");
    sendToPeer("leftHand", "enable");
    sendToPeer("rightArm", "enable");
    sendToPeer("leftArm", "enable");
    sendToPeer("torso", "enable");
  }

  /**
   * This method will try to launch a python command with error handling
   * 
   * @param gesture
   *          the gesture
   * @return gesture result
   */
  public String execGesture(String gesture) {

    lastGestureExecuted = gesture;
    if (python == null) {
      log.warn("execGesture : No jython engine...");
      return null;
    }
    subscribe(python.getName(), "publishStatus", this.getName(), "onGestureStatus");
    startedGesture(lastGestureExecuted);
    return python.evalAndWait(gesture);
  }

  public void finishedGesture() {
    finishedGesture("unknown");
  }

  // public State publishState(State state) {
  // return state;
  // }

  public void finishedGesture(String nameOfGesture) {
    if (gestureAlreadyStarted) {
      waitTargetPos();
      // RobotCanMoveRandom = true;
      gestureAlreadyStarted = false;
      log.info("gesture : {} finished...", nameOfGesture);
    }
  }

  public void fullSpeed() {
    sendToPeer("head", "fullSpeed");
    sendToPeer("rightHand", "fullSpeed");
    sendToPeer("leftHand", "fullSpeed");
    sendToPeer("rightArm", "fullSpeed");
    sendToPeer("leftArm", "fullSpeed");
    sendToPeer("torso", "fullSpeed");
  }

  public String get(String key) {
    String ret = localize(key);
    if (ret != null) {
      return ret;
    }
    return "not yet translated";
  }

  public InMoov2Arm getArm(String side) {
    if (!"left".equals(side) && !"right".equals(side)) {
      error("side must be left or right - instead of %s", side);
      return null;
    }
    return (InMoov2Arm) getPeer(side + "Arm");
  }

  public InMoov2Hand getHand(String side) {
    if (!"left".equals(side) && !"right".equals(side)) {
      error("side must be left or right - instead of %s", side);
      return null;
    }
    return (InMoov2Hand) getPeer(side + "Hand");
  }

  public InMoov2Head getHead() {
    return (InMoov2Head) getPeer("head");
  }

  /**
   * finds most recent activity
   * 
   * @return the timestamp of the last activity time.
   */
  public Long getLastActivityTime() {
    try {

      Long lastActivityTime = 0L;

      Long head = (Long) sendToPeerBlocking("head", "getLastActivityTime", getName());
      Long leftArm = (Long) sendToPeerBlocking("leftArm", "getLastActivityTime", getName());
      Long rightArm = (Long) sendToPeerBlocking("rightArm", "getLastActivityTime", getName());
      Long leftHand = (Long) sendToPeerBlocking("leftHand", "getLastActivityTime", getName());
      Long rightHand = (Long) sendToPeerBlocking("rightHand", "getLastActivityTime", getName());
      Long torso = (Long) sendToPeerBlocking("torso", "getLastActivityTime", getName());

      lastActivityTime = Math.max(head, leftArm);
      lastActivityTime = Math.max(lastActivityTime, rightArm);
      lastActivityTime = Math.max(lastActivityTime, leftHand);
      lastActivityTime = Math.max(lastActivityTime, rightHand);
      lastActivityTime = Math.max(lastActivityTime, torso);

      return lastActivityTime;

    } catch (Exception e) {
      error(e);
      return null;
    }

  }

  public InMoov2Arm getLeftArm() {
    return (InMoov2Arm) getPeer("leftArm");
  }

  public InMoov2Hand getLeftHand() {
    return (InMoov2Hand) getPeer("leftHand");
  }

  @Override
  public Map<String, Locale> getLocales() {
    return locales;
  }

  public InMoov2Arm getRightArm() {
    return (InMoov2Arm) getPeer("rightArm");
  }

  public InMoov2Hand getRightHand() {
    return (InMoov2Hand) getPeer("rightHand");
  }

  public Simulator getSimulator() {
    return (Simulator) getPeer("simulator");
  }

  public InMoov2Torso getTorso() {
    return (InMoov2Torso) getPeer("torso");
  }

  public void halfSpeed() {
    sendToPeer("head", "setSpeed", 25.0, 25.0, 25.0, 25.0, 100.0, 25.0);
    sendToPeer("rightHand", "setSpeed", 30.0, 30.0, 30.0, 30.0, 30.0, 30.0);
    sendToPeer("leftHand", "setSpeed", 30.0, 30.0, 30.0, 30.0, 30.0, 30.0);
    sendToPeer("rightArm", "setSpeed", 25.0, 25.0, 25.0, 25.0);
    sendToPeer("leftArm", "setSpeed", 25.0, 25.0, 25.0, 25.0);
    sendToPeer("torso", "setSpeed", 20.0, 20.0, 20.0);
  }

  public boolean isCameraOn() {
    if (opencv != null) {
      if (opencv.isCapturing()) {
        return true;
      }
    }
    return false;
  }

  public boolean isMute() {
    return mute;
  }

  public void loadGestures() {
    loadGestures(getResourceDir() + fs + "gestures");
  }

  /**
   * This blocking method will look at all of the .py files in a directory. One
   * by one it will load the files into the python interpreter. A gesture python
   * file should contain 1 method definition that is the same as the filename.
   * 
   * @param directory
   *          - the directory that contains the gesture python files.
   * @return true/false
   */
  public boolean loadGestures(String directory) {
    speakBlocking(get("STARTINGGESTURES"));

    // iterate over each of the python files in the directory
    // and load them into the python interpreter.
    String extension = "py";
    Integer totalLoaded = 0;
    Integer totalError = 0;

    File dir = new File(directory);
    dir.mkdirs();

    if (dir.exists()) {
      for (File f : dir.listFiles()) {
        if (FilenameUtils.getExtension(f.getAbsolutePath()).equalsIgnoreCase(extension)) {
          if (loadFile(f.getAbsolutePath()) == true) {
            totalLoaded += 1;
            String methodName = f.getName().substring(0, f.getName().length() - 3) + "()";
            gestures.add(methodName);
          } else {
            error("could not load %s", f.getName());
            totalError += 1;
          }
        } else {
          log.info("{} is not a {} file", f.getAbsolutePath(), extension);
        }
      }
    }
    info("%s Gestures loaded, %s Gestures with error", totalLoaded, totalError);
    broadcastState();
    if (totalError > 0) {
      speakAlert(get("GESTURE_ERROR"));
      return false;
    }
    return true;
  }

  public String captureGesture() {
    return captureGesture(null);
  }

  public String captureGesture(String gestureName) {
    StringBuffer script = new StringBuffer();
    Date date = new Date();

    script.append("# - " + date + " - Captured gesture :\n");

    if (gestureName != null) {
      script.append(String.format("def %s():\n", gestureName));
    }

    try {
      script.append(sendToPeerBlocking("head", "getScript", getName()));
      script.append(sendToPeerBlocking("leftArm", "getScript", getName()));
      script.append(sendToPeerBlocking("rightArm", "getScript", getName()));
      script.append(sendToPeerBlocking("leftHand", "getScript", getName()));
      script.append(sendToPeerBlocking("rightHand", "getScript", getName()));
      script.append(sendToPeerBlocking("torso", "getScript", getName()));

    } catch (Exception e) {
      error(e);
    }

    send("python", "appendScript", script.toString());

    return script.toString();
  }

  public void cameraOff() {
    if (opencv != null) {
      opencv.stopCapture();
      opencv.disableAll();
    }
  }

  public void cameraOn() {
    try {
      if (opencv == null) {
        startOpenCV();
      }
      opencv.capture();
    } catch (Exception e) {
      error(e);
    }
  }

  public void moveLeftArm(Double bicep, Double rotate, Double shoulder, Double omoplate) {
    moveArm("left", bicep, rotate, shoulder, omoplate);
  }

  public void moveRightArm(Double bicep, Double rotate, Double shoulder, Double omoplate) {
    moveArm("right", bicep, rotate, shoulder, omoplate);
  }

  public void moveArm(String which, Double bicep, Double rotate, Double shoulder, Double omoplate) {
    InMoov2Arm arm = getArm(which);
    if (arm == null) {
      info("%s arm not started", which);
      return;
    }
    arm.moveTo(bicep, rotate, shoulder, omoplate);
  }

  public void moveEyelids(Double eyelidleftPos, Double eyelidrightPos) {
    sendToPeer("head", "moveEyelids", eyelidleftPos, eyelidrightPos);
  }

  public void moveEyes(Double eyeX, Double eyeY) {
    moveHead(null, null, eyeX, eyeY, null, null);
  }

  public void moveRightHand(Double thumb, Double index, Double majeure, Double ringFinger, Double pinky, Double wrist) {
    moveHand("right", thumb, index, majeure, ringFinger, pinky, wrist);
  }

  public void moveRightHand(Integer thumb, Integer index, Integer majeure, Integer ringFinger, Integer pinky, Integer wrist) {
    moveHand("right", (double) thumb, (double) index, (double) majeure, (double) ringFinger, (double) pinky, (double) wrist);
  }

  public void moveLeftHand(Double thumb, Double index, Double majeure, Double ringFinger, Double pinky, Double wrist) {
    moveHand("left", thumb, index, majeure, ringFinger, pinky, wrist);
  }

  public void moveLeftHand(Integer thumb, Integer index, Integer majeure, Integer ringFinger, Integer pinky, Integer wrist) {
    moveHand("left", (double) thumb, (double) index, (double) majeure, (double) ringFinger, (double) pinky, (double) wrist);
  }

  public void moveHand(String which, Double thumb, Double index, Double majeure, Double ringFinger, Double pinky) {
    moveHand(which, thumb, index, majeure, ringFinger, pinky, null);
  }

  public void moveHand(String which, Double thumb, Double index, Double majeure, Double ringFinger, Double pinky, Double wrist) {
    // the "right" way
    sendToPeer(which + "Hand", "moveTo", thumb, index, majeure, ringFinger, pinky, wrist);
  }

  public void moveHead(Double neck, Double rothead) {
    moveHead(neck, rothead, null, null, null, null);
  }

  public void moveHead(Double neck, Double rothead, Double eyeX, Double eyeY, Double jaw) {
    moveHead(neck, rothead, eyeX, eyeY, jaw, null);
  }

  public void moveHead(Double neck, Double rothead, Double rollNeck) {
    moveHead(rollNeck, rothead, null, null, null, rollNeck);
  }

  public void moveHead(Integer neck, Integer rothead, Integer rollNeck) {
    moveHead((double) rollNeck, (double) rothead, null, null, null, (double) rollNeck);
  }

  public void moveHead(Double neck, Double rothead, Double eyeX, Double eyeY, Double jaw, Double rollNeck) {
    // the "right" way
    sendToPeer("head", "moveTo", neck, rothead, eyeX, eyeY, jaw, rollNeck);
  }

  public void moveHeadBlocking(Double neck, Double rothead) {
    moveHeadBlocking(neck, rothead, null);
  }

  public void moveHeadBlocking(Double neck, Double rothead, Double rollNeck) {
    moveHeadBlocking(neck, rothead, null, null, null, rollNeck);
  }

  public void moveHeadBlocking(Double neck, Double rothead, Double eyeX, Double eyeY, Double jaw) {
    moveHeadBlocking(neck, rothead, eyeX, eyeY, jaw, null);
  }

  public void moveHeadBlocking(Double neck, Double rothead, Double eyeX, Double eyeY, Double jaw, Double rollNeck) {
    // the "right" way
    sendToPeer("head", "moveToBlocking", neck, rothead, eyeX, eyeY, jaw, rollNeck);
  }

  public void moveTorso(Double topStom, Double midStom, Double lowStom) {
    // the "right" way
    sendToPeer("torso", "moveTo", topStom, midStom, lowStom);
  }

  public void moveTorsoBlocking(Double topStom, Double midStom, Double lowStom) {
    // the "right" way
    sendToPeer("torso", "moveToBlocking", topStom, midStom, lowStom);
  }

  public void onGestureStatus(Status status) {
    if (!status.equals(Status.success()) && !status.equals(Status.warn("Python process killed !"))) {
      error("I cannot execute %s, please check logs", lastGestureExecuted);
    }
    finishedGesture(lastGestureExecuted);
    unsubscribe(python.getName(), "publishStatus", this.getName(), "onGestureStatus");
  }

  @Override
  public void onJoystickInput(JoystickData input) throws Exception {
    // TODO Auto-generated method stub

  }

  public OpenCVData onOpenCVData(OpenCVData data) {
    return data;
  }

  @Override
  public void onText(String text) {
    // FIXME - we should be able to "re"-publish text but text is coming from
    // different sources
    // some might be coming from the ear - some from the mouth ... - there has
    // to be a distinction
    log.info("onText - {}", text);
    invoke("publishText", text);
  }

  // TODO FIX/CHECK this, migrate from python land
  public void powerDown() {

    rest();
    purgeTasks();
    disable();

    if (ear != null) {
      ear.lockOutAllGrammarExcept("power up");
    }

    python.execMethod("power_down");
  }

  // TODO FIX/CHECK this, migrate from python land
  public void powerUp() {
    enable();
    rest();

    if (ear != null) {
      ear.clearLock();
    }

    beginCheckingOnInactivity();

    python.execMethod("power_up");
  }

  /**
   * all published text from InMoov2 - including ProgramAB
   */
  @Override
  public String publishText(String text) {
    return text;
  }

  public void releaseService() {
    try {
      disable();
      super.releaseService();
    } catch (Exception e) {
      error(e);
    }
  }

  // FIXME NO DIRECT REFERENCES - publishRest --> (onRest) --> rest
  public void rest() {
    sendToPeer("head", "rest");
    sendToPeer("rightHand", "rest");
    sendToPeer("leftHand", "rest");
    sendToPeer("rightArm", "rest");
    sendToPeer("leftArm", "rest");
    sendToPeer("torso", "rest");
  }

  public void setLeftArmSpeed(Double bicep, Double rotate, Double shoulder, Double omoplate) {
    setArmSpeed("left", bicep, rotate, shoulder, omoplate);
  }

  public void setLeftArmSpeed(Integer bicep, Integer rotate, Integer shoulder, Integer omoplate) {
    setArmSpeed("left", (double) bicep, (double) rotate, (double) shoulder, (double) omoplate);
  }

  public void setRightArmSpeed(Double bicep, Double rotate, Double shoulder, Double omoplate) {
    setArmSpeed("right", bicep, rotate, shoulder, omoplate);
  }

  public void setRightArmSpeed(Integer bicep, Integer rotate, Integer shoulder, Integer omoplate) {
    setArmSpeed("right", (double) bicep, (double) rotate, (double) shoulder, (double) omoplate);
  }

  public void setArmSpeed(String which, Double bicep, Double rotate, Double shoulder, Double omoplate) {
    InMoov2Arm arm = getArm(which);
    if (arm == null) {
      warn("%s arm not started", which);
      return;
    }
    arm.setSpeed(bicep, rotate, shoulder, omoplate);
  }

  @Deprecated
  public void setArmVelocity(String which, Double bicep, Double rotate, Double shoulder, Double omoplate) {
    setArmSpeed(which, bicep, rotate, shoulder, omoplate);
  }

  public void setAutoDisable(Boolean param) {

    sendToPeer("head", "setAutoDisable", param);
    sendToPeer("rightHand", "setAutoDisable", param);
    sendToPeer("leftHand", "setAutoDisable", param);
    sendToPeer("rightArm", "setAutoDisable", param);
    sendToPeer("leftArm", "setAutoDisable", param);
    sendToPeer("torso", "setAutoDisable", param);
  }

  public void setHandSpeed(String which, Double thumb, Double index, Double majeure, Double ringFinger, Double pinky) {
    setHandSpeed(which, thumb, index, majeure, ringFinger, pinky, null);
  }

  public void setLeftHandSpeed(Double thumb, Double index, Double majeure, Double ringFinger, Double pinky, Double wrist) {
    setHandSpeed("left", thumb, index, majeure, ringFinger, pinky, wrist);
  }

  public void setLeftHandSpeed(Integer thumb, Integer index, Integer majeure, Integer ringFinger, Integer pinky, Integer wrist) {
    setHandSpeed("left", (double) thumb, (double) index, (double) majeure, (double) ringFinger, (double) pinky, (double) wrist);
  }

  public void setRightHandSpeed(Double thumb, Double index, Double majeure, Double ringFinger, Double pinky, Double wrist) {
    setHandSpeed("right", thumb, index, majeure, ringFinger, pinky, wrist);
  }

  public void setRightHandSpeed(Integer thumb, Integer index, Integer majeure, Integer ringFinger, Integer pinky, Integer wrist) {
    setHandSpeed("right", (double) thumb, (double) index, (double) majeure, (double) ringFinger, (double) pinky, (double) wrist);
  }

  public void setHandSpeed(String which, Double thumb, Double index, Double majeure, Double ringFinger, Double pinky, Double wrist) {
    InMoov2Hand hand = getHand(which);
    if (hand == null) {
      warn("%s hand not started", which);
      return;
    }
    hand.setSpeed(thumb, index, majeure, ringFinger, pinky, wrist);
  }

  @Deprecated
  public void setHandVelocity(String which, Double thumb, Double index, Double majeure, Double ringFinger, Double pinky) {
    setHandSpeed(which, thumb, index, majeure, ringFinger, pinky, null);
  }

  @Deprecated
  public void setHandVelocity(String which, Double thumb, Double index, Double majeure, Double ringFinger, Double pinky, Double wrist) {
    setHandSpeed(which, thumb, index, majeure, ringFinger, pinky, wrist);
  }

  public void setHeadSpeed(Double rothead, Double neck) {
    setHeadSpeed(rothead, neck, null, null, null);
  }

  public void setHeadSpeed(Double rothead, Double neck, Double rollNeck) {
    setHeadSpeed(rothead, neck, null, null, null, rollNeck);
  }

  public void setHeadSpeed(Integer rothead, Integer neck, Integer rollNeck) {
    setHeadSpeed((double) rothead, (double) neck, null, null, null, (double) rollNeck);
  }

  public void setHeadSpeed(Double rothead, Double neck, Double eyeXSpeed, Double eyeYSpeed, Double jawSpeed) {
    setHeadSpeed(rothead, neck, eyeXSpeed, eyeYSpeed, jawSpeed, null);
  }

  public void setHeadSpeed(Double rothead, Double neck, Double eyeXSpeed, Double eyeYSpeed, Double jawSpeed, Double rollNeckSpeed) {
    sendToPeer("head", "setSpeed", neck, eyeXSpeed, eyeYSpeed, jawSpeed, rollNeckSpeed);
  }

  @Deprecated
  public void setHeadVelocity(Double rothead, Double neck) {
    setHeadSpeed(rothead, neck, null, null, null, null);
  }

  @Deprecated
  public void setHeadVelocity(Double rothead, Double neck, Double rollNeck) {
    setHeadSpeed(rothead, neck, null, null, null, rollNeck);
  }

  @Deprecated
  public void setHeadVelocity(Double rothead, Double neck, Double eyeXSpeed, Double eyeYSpeed, Double jawSpeed) {
    setHeadSpeed(rothead, neck, eyeXSpeed, eyeYSpeed, jawSpeed, null);
  }

  @Deprecated
  public void setHeadVelocity(Double rothead, Double neck, Double eyeXSpeed, Double eyeYSpeed, Double jawSpeed, Double rollNeckSpeed) {
    setHeadSpeed(rothead, neck, eyeXSpeed, eyeYSpeed, jawSpeed, rollNeckSpeed);
  }

  @Override
  public void setLocale(String code) {

    if (code == null) {
      log.warn("setLocale null");
      return;
    }

    // filter of the set of supported locales
    if (!Locale.hasLanguage(locales, code)) {
      error("InMoov does not support %s only %s", code, locales.keySet());
      return;
    }

    super.setLocale(code);

    locale = new Locale(code);

    // super.setLocale(code);
    for (ServiceInterface si : Runtime.getLocalServices().values()) {
      if (!si.equals(this) && !si.isRuntime()) {
        si.setLocale(code);
      }
    }
  }

  public void setMute(boolean mute) {
    info("Set mute to %s", mute);
    this.mute = mute;
    sendToPeer("mouth", "setMute", mute);
    broadcastState();
  }

  public void setNeopixelAnimation(String animation, Integer red, Integer green, Integer blue, Integer speed) {
    sendToPeer("neopixel", "animation", red, green, blue, speed);
  }

  public String setSpeechType(String speechType) {
    // changing runtime plan
    Plan plan = Runtime.getPlan();
    plan.remove(getPeerName("mouth"));
    Runtime.load(getPeerName("mouth"), speechType);
    // broadcastState();
    return speechType;
  }

  public void setTorsoSpeed(Double topStom, Double midStom, Double lowStom) {
    sendToPeer("torso", "setSpeed", topStom, midStom, lowStom);
  }

  public void setTorsoSpeed(Integer topStom, Integer midStom, Integer lowStom) {
    setTorsoSpeed((double) topStom, (double) midStom, (double) lowStom);
  }

  @Deprecated
  public void setTorsoVelocity(Double topStom, Double midStom, Double lowStom) {
    setTorsoSpeed(topStom, midStom, lowStom);
  }

  public boolean setAllVirtual(boolean virtual) {
    Runtime.setAllVirtual(virtual);
    speakBlocking(get("STARTINGVIRTUALHARD"));
    return virtual;
  }

  public void setVoice(String name) {
    if (mouth != null) {
      mouth.setVoice(name);
      voiceSelected = name;
      speakBlocking(String.format("%s %s", get("SETLANG"), name));
    }
  }

  public void speak(String toSpeak) {
    sendToPeer("mouth", "speak", toSpeak);
  }

  public void speakAlert(String toSpeak) {
    speakBlocking(get("ALERT"));
    speakBlocking(toSpeak);
  }

  public void speakBlocking(String speak) {
    speakBlocking(speak, (Object[]) null);
  }

  // FIXME - publish text regardless if mouth exists ...
  public void speakBlocking(String format, Object... args) {

    if (format == null) {
      return;
    }

    String toSpeak = format;
    if (args != null) {
      toSpeak = String.format(format, args);
    }

    // FIXME - publish onText when listening
    invoke("publishText", toSpeak);

    if (!mute && isPeerStarted("mouth")) {
      // sendToPeer("mouth", "speakBlocking", toSpeak);
      // invokePeer("mouth", "speakBlocking", toSpeak);
      sendToPeer("mouth", "speakBlocking", toSpeak);
    }
  }

  public void startAll() throws Exception {
    startAll(null, null);
  }

  public void startAll(String leftPort, String rightPort) throws Exception {
    startMouth();
    startChatBot();

    // startHeadTracking();
    // startEyesTracking();
    // startOpenCV();
    startEar();

    startServos();
    // startMouthControl(head.jaw, mouth);

    speakBlocking(get("STARTINGSEQUENCE"));
  }

  public ProgramAB startChatBot() {

    try {
      chatBot = (ProgramAB) startPeer("chatBot");

      if (locale != null) {
        chatBot.setCurrentBotName(locale.getTag());
      }

      speakBlocking(get("CHATBOTACTIVATED"));

      chatBot.attachTextPublisher(ear);

      // this.attach(chatBot); FIXME - attach as a TextPublisher - then
      // re-publish
      // FIXME - deal with language
      // speakBlocking(get("CHATBOTACTIVATED"));
      chatBot.repetitionCount(10);
      // chatBot.setPath(getResourceDir() + fs + "chatbot");
      chatBot.setPath(getDataDir() + fs + "chatbot");
      chatBot.startSession("default", locale.getTag());
      // reset some parameters to default...
      chatBot.setPredicate("topic", "default");
      chatBot.setPredicate("questionfirstinit", "");
      chatBot.setPredicate("tmpname", "");
      chatBot.setPredicate("null", "");
      // load last user session
      if (!chatBot.getPredicate("name").isEmpty()) {
        if (chatBot.getPredicate("lastUsername").isEmpty() || chatBot.getPredicate("lastUsername").equals("unknown") || chatBot.getPredicate("lastUsername").equals("default")) {
          chatBot.setPredicate("lastUsername", chatBot.getPredicate("name"));
        }
      }
      chatBot.setPredicate("parameterHowDoYouDo", "");
      try {
        chatBot.savePredicates();
      } catch (IOException e) {
        log.error("saving predicates threw", e);
      }
      htmlFilter = (HtmlFilter) startPeer("htmlFilter");// Runtime.start("htmlFilter",
      // "HtmlFilter");
      chatBot.attachTextListener(htmlFilter);
      htmlFilter.attachTextListener((TextListener) getPeer("mouth"));
      chatBot.attachTextListener(this);
      // start session based on last recognized person
      // if (!chatBot.getPredicate("default", "lastUsername").isEmpty() &&
      // !chatBot.getPredicate("default", "lastUsername").equals("unknown")) {
      // chatBot.startSession(chatBot.getPredicate("lastUsername"));
      // }
      if (chatBot.getPredicate("default", "firstinit").isEmpty() || chatBot.getPredicate("default", "firstinit").equals("unknown")
          || chatBot.getPredicate("default", "firstinit").equals("started")) {
        chatBot.startSession(chatBot.getPredicate("default", "lastUsername"));
        chatBot.getResponse("FIRST_INIT");
      } else {
        chatBot.startSession(chatBot.getPredicate("default", "lastUsername"));
        chatBot.getResponse("WAKE_UP");
      }
    } catch (Exception e) {
      speak("could not load chatBot");
      error(e.getMessage());
      speak(e.getMessage());
    }
    broadcastState();
    return chatBot;
  }

  public SpeechRecognizer startEar() {

    ear = (SpeechRecognizer) startPeer("ear");
    ear.attachSpeechSynthesis((SpeechSynthesis) getPeer("mouth"));
    ear.attachTextListener(chatBot);

    speakBlocking(get("STARTINGEAR"));
    broadcastState();
    return ear;
  }

  public void startedGesture() {
    startedGesture("unknown");
  }

  public void startedGesture(String nameOfGesture) {
    if (gestureAlreadyStarted) {
      warn("Warning 1 gesture already running, this can break spacetime and lot of things");
    } else {
      log.info("Starting gesture : {}", nameOfGesture);
      gestureAlreadyStarted = true;
      // RobotCanMoveRandom = false;
    }
  }

  // FIXME - universal (good) way of handling all exceptions - ie - reporting
  // back to the user the problem in a short concise way but have
  // expandable detail in appropriate places
  public OpenCV startOpenCV() {
    speakBlocking(get("STARTINGOPENCV"));
    opencv = (OpenCV) startPeer("opencv");
    subscribeTo(opencv.getName(), "publishOpenCVData");
    return opencv;
  }

  public OpenCV getOpenCV() {
    return opencv;
  }

  public void setOpenCV(OpenCV opencv) {
    this.opencv = opencv;
  }

  // TODO - general objective "might" be to reduce peers down to something
  // that does not need a reference - where type can be switched before creation
  // and the only thing needed is pubs/subs that are not handled in abstracts
  public SpeechSynthesis startMouth() {

    // FIXME - set type ??? - maybe a good product of InMoov
    // if "new" type cannot necessarily grab yml file
    // setMouthType

    // FIXME - bad to have a reference, should only need the "name" of the
    // service !!!
    mouth = (SpeechSynthesis) startPeer("mouth");

    // voices = mouth.getVoices();
    // Voice voice = mouth.getVoice();
    // if (voice != null) {
    // voiceSelected = voice.getName();
    // }

    if (mute) {
      mouth.setMute(true);
    }

    mouth.attachSpeechRecognizer(ear);
    // mouth.attach(htmlFilter); // same as chatBot not needed

    // this.attach((Attachable) mouth);
    // if (ear != null) ....

    broadcastState();

    speakBlocking(get("STARTINGMOUTH"));
    if (Platform.isVirtual()) {
      speakBlocking(get("STARTINGVIRTUALHARD"));
    }
    speakBlocking(get("WHATISTHISLANGUAGE"));

    return mouth;
  }

  public void startServos() {
    startPeer("head");
    startPeer("leftArm");
    startPeer("leftHand");
    startPeer("rightArm");
    startPeer("rightHand");
    startPeer("torso");
  }

  // FIXME .. externalize in a json file included in InMoov2
  public Simulator startSimulator() throws Exception {
    Simulator si = (Simulator) startPeer("simulator");
    return si;
  }

  public void stop() {
    sendToPeer("head", "stop");
    sendToPeer("rightHand", "stop");
    sendToPeer("leftHand", "stop");
    sendToPeer("rightArm", "stop");
    sendToPeer("leftArm", "stop");
    sendToPeer("torso", "stop");
  }

  public void stopGesture() {
    Python p = (Python) Runtime.getService("python");
    p.stop();
  }

  public ServiceInterface startPeer(String peer) {
    speakBlocking(get("STARTING" + peer.toUpperCase()));

    // FIXME - do reflective look for local vars named the same thing
    // to set the field

    ServiceInterface si = super.startPeer(peer);

    return si;
  }

  public void releasePeer(String peer) {
    speakBlocking(get("STOP" + peer.toUpperCase()));
    super.releasePeer(peer);
  }

  public void stopNeopixelAnimation() {
    sendToPeer("neopixel", "clear");
  }

  public void waitTargetPos() {

    sendToPeer("head", "waitTargetPos");
    sendToPeer("rightHand", "waitTargetPos");
    sendToPeer("leftHand", "waitTargetPos");
    sendToPeer("rightArm", "waitTargetPos");
    sendToPeer("leftArm", "waitTargetPos");
    sendToPeer("torso", "waitTargetPos");
  }

  @Override
  public void attachTextListener(String name) {
    addListener("publishText", name);
  }

  public Tracking getEyesTracking() {
    return eyesTracking;
  }

  public Tracking getHeadTracking() {
    return headTracking;
  }

  public void startBrain() {
    startChatBot();
  }

  public void startMouthControl() {
    speakBlocking(get("STARTINGMOUTHCONTROL"));
    mouthControl = (MouthControl) startPeer("mouthControl");
    InMoov2Head head = getHead();
    if (head != null) {
      mouthControl.attach(head.getPeer("jaw"));
    }
    mouthControl.attach((Attachable) getPeer("mouth"));
  }

  // -----------------------------------------------------------------------------
  // These are methods added that were in InMoov1 that we no longer had in
  // InMoov2.
  // From original InMoov1 so we don't loose the

  @Override
  public void onJointAngles(Map<String, Double> angleMap) {
    log.info("onJointAngles {}", angleMap);
    // here we can make decisions on what ik sets we want to use and
    // what body parts are to move
    for (String name : angleMap.keySet()) {
      ServiceInterface si = Runtime.getService(name);
      if (si != null && si instanceof ServoControl) {
        ((Servo) si).moveTo(angleMap.get(name));
      }
    }
  }

  @Override
  public ServiceConfig getConfig() {
    InMoov2Config config = new InMoov2Config();
    return config;
  }

  @Override
  public ServiceConfig apply(ServiceConfig c) {
    InMoov2Config config = (InMoov2Config) c;
    try {

      if (config.locale != null) {
        setLocale(config.locale);
      }

      if (config.loadGestures) {
        loadGestures();
      }

      if (config.heartbeat) {
        startHeartbeat();
      } else {
        stopHeartbeat();
      }

    } catch (Exception e) {
      error(e);
    }

    return c;
  }

  public void startHeartbeat() {
    addTask(1000, "publishHeartbeat");
  }

  public void stopHeartbeat() {
    purgeTask("publishHeartbeat");
  }

  public String publishHeartbeat() {
    return getName();
  }

  // ???? - seems like a good pattern dunno what to do
  // Overriding and polymorphism is a nice way to reduce code
  // public void onHeartbeat(String name) {
  //
  // }

  public static void main(String[] args) {
    try {

      LoggingFactory.init(Level.INFO);
      // Platform.setVirtual(true);
      // Runtime.start("s01", "Servo");
      // Runtime.start("intro", "Intro");

      Runtime.startConfig("dewey-2");

      boolean done = true;
      if (done) {
        return;
      }

      InMoov2 i01 = (InMoov2) Runtime.start("i01", "InMoov2");
      i01.startSimulator();
      Plan plan = Runtime.load("webgui", "WebGui");
      WebGuiConfig webgui = (WebGuiConfig) plan.get("webgui");
      webgui.autoStartBrowser = false;
      Runtime.startConfig("webgui");
      Runtime.start("webgui", "WebGui");

      Random random = (Random) Runtime.start("random", "Random");

      random.addRandom(3000, 8000, "i01", "setLeftArmSpeed", 8.0, 25.0, 8.0, 25.0, 8.0, 25.0, 8.0, 25.0);
      random.addRandom(3000, 8000, "i01", "setRightArmSpeed", 8.0, 25.0, 8.0, 25.0, 8.0, 25.0, 8.0, 25.0);

      random.addRandom(3000, 8000, "i01", "moveLeftArm", 0.0, 5.0, 85.0, 95.0, 25.0, 30.0, 10.0, 15.0);
      random.addRandom(3000, 8000, "i01", "moveRightArm", 0.0, 5.0, 85.0, 95.0, 25.0, 30.0, 10.0, 15.0);

      random.addRandom(3000, 8000, "i01", "setLeftHandSpeed", 8.0, 25.0, 8.0, 25.0, 8.0, 25.0, 8.0, 25.0, 8.0, 25.0, 8.0, 25.0);
      random.addRandom(3000, 8000, "i01", "setRightHandSpeed", 8.0, 25.0, 8.0, 25.0, 8.0, 25.0, 8.0, 25.0, 8.0, 25.0, 8.0, 25.0);

      random.addRandom(3000, 8000, "i01", "moveRightHand", 10.0, 160.0, 10.0, 60.0, 10.0, 60.0, 10.0, 60.0, 10.0, 60.0, 130.0, 175.0);
      random.addRandom(3000, 8000, "i01", "moveLeftHand", 10.0, 160.0, 10.0, 60.0, 10.0, 60.0, 10.0, 60.0, 10.0, 60.0, 5.0, 40.0);

      random.addRandom(200, 1000, "i01", "setHeadSpeed", 8.0, 20.0, 8.0, 20.0, 8.0, 20.0);
      random.addRandom(200, 1000, "i01", "moveHead", 70.0, 110.0, 65.0, 115.0, 70.0, 110.0);

      random.addRandom(200, 1000, "i01", "setTorsoSpeed", 2.0, 5.0, 2.0, 5.0, 2.0, 5.0);
      random.addRandom(200, 1000, "i01", "moveTorso", 85.0, 95.0, 88.0, 93.0, 70.0, 110.0);

      random.save();

      i01.startChatBot();

      i01.startAll("COM3", "COM4");
      Runtime.start("python", "Python");

    } catch (Exception e) {
      log.error("main threw", e);
    }
  }

  @Override
  public void onRegistered(Registration registration) {
    // TODO Auto-generated method stub

  }

  @Override
  public void onStopped(String name) {
    // TODO Auto-generated method stub

  }

  @Override
  public void onReleased(String name) {
    // TODO Auto-generated method stub

  }

  public String publishPeerStarted(String peerKey) {
    // if ("mouth".equals(peerKey)) {
    // SpeechSynthesis mouth = (SpeechSynthesis)getPeer(peerKey);
    // voices = mouth.getVoices();
    // Voice voice = mouth.getVoice();
    // if (voice != null) {
    // voiceSelected = voice.getName();
    // }
    // broadcastState();
    // }
    return peerKey;
  }

}
