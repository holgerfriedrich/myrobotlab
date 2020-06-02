package org.myrobotlab.service;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import org.myrobotlab.framework.Service;
import org.myrobotlab.framework.ServiceType;
import org.myrobotlab.framework.repo.Repo;
import org.myrobotlab.io.FileIO;
import org.myrobotlab.logging.Level;
import org.myrobotlab.logging.LoggerFactory;
import org.myrobotlab.logging.LoggingFactory;
import org.myrobotlab.service.abstracts.AbstractSpeechSynthesis;
import org.myrobotlab.service.interfaces.PortConnector;
import org.myrobotlab.service.interfaces.ServoControl;
import org.myrobotlab.service.interfaces.ServoController;
import org.slf4j.Logger;

public class Intro extends Service {

  public class TutorialInfo {

    public String id;
    public boolean isInstalled;
    public String script;
    public String[] servicesRequired;
    public String title;

  }

  public final static Logger log = LoggerFactory.getLogger(Intro.class);

  private static final long serialVersionUID = 1L;
  
  public boolean isServoActivated = false;
  public boolean isSpeechActivated = false;

  transient ServoControl servo;
  transient ServoController controller;
  transient AbstractSpeechSynthesis speech;

  Map<String, TutorialInfo> tutorials = new TreeMap<>();

  public Intro(String n, String id) {
    super(n, id);
    try {
      Runtime runtime = Runtime.getInstance();
      Repo repo = runtime.getRepo();
      TutorialInfo tuto = new TutorialInfo();
      tuto.id = "servo-hardware";
      tuto.title = "Servo with Arduino Hardware";
      tuto.servicesRequired = new String[] { "Servo", "Arduino" };
      tuto.isInstalled = repo.isInstalled(Servo.class) && repo.isInstalled(Arduino.class);
      tuto.script = Service.getServiceScript(Servo.class);
      tutorials.put(tuto.title, tuto);
    } catch (Exception e) {
      log.error("Intro constructor threw", e);
    }
  }

  public void checkInstalled(String forTutorial, String serviceType) {
    Runtime runtime = Runtime.getInstance();
    Repo repo = runtime.getRepo();

    TutorialInfo tutorial = new TutorialInfo();
    tutorial.title = forTutorial;
    tutorial.isInstalled = repo.isInstalled(serviceType);
  }

  public boolean isServoActivated() {
    return isServoActivated;
  }
  
  /**
   * execute an Intro resource script
   * @param introScriptName
   */
  public void execScript(String introScriptName) {
    try {
      Python p = (Python)Runtime.start("python", "Python");
      String script = getResourceAsString(introScriptName);
      p.exec(script, true);
    } catch (Exception e) {
      error("unable to execute script %s", introScriptName); 
    }
  }

  /**
	 * This method will load a python file into the python interpreter.
	 */
  @Deprecated
	public boolean loadFile(String file) {
		File f = new File(file);
		Python p = (Python) Runtime.getService("python");
		log.info("Loading  Python file {}", f.getAbsolutePath());
		if (p == null) {
			log.error("Python instance not found");
			return false;
		}
		String script = null;
		try {
			script = FileIO.toString(f.getAbsolutePath());
		} catch (IOException e) {
			log.error("IO Error loading file : ", e);
			return false;
		}
		// evaluate the scripts in a blocking way.
		boolean result = p.exec(script, true);
		if (!result) {
			log.error("Error while loading file {}", f.getAbsolutePath());
			return false;
		} else {
			log.debug("Successfully loaded {}", f.getAbsolutePath());
		}
		return true;
	}

  public void speakBlocking(String text) {
    if (speech != null) {
      speech.speak(text);
    }
  }

  public void startSpeech() {
    speech = (AbstractSpeechSynthesis)Runtime.start("speech", "WebKitSpeechSynthesis");
    isSpeechActivated = true;
  }

  public void stopSpeech() {
    if (speech != null) {
      speech.releaseService();
    }
    isSpeechActivated = false;
    speech = null;
  }
  
  @Deprecated
  public void startServo() {
    startServo("COM3", 3);
  }
  
  @Deprecated
  public void startServo(String port) {
    startServo(port, 3);
  }

  @Deprecated
  public void startServo(String port, int pin) {
  
    if (servo == null) {
      speakBlocking("starting servo");
      isServoActivated = true;

      servo = (ServoControl) startPeer("servo");

      if (port != null) {
        try {
          speakBlocking(port);
          controller = (ServoController) startPeer("controller");
          ((PortConnector)controller).connect(port);
          controller.attach(servo);
        } catch (Exception e) {
          error(e);
        }
      }
    }
  }

  @Deprecated
  public void stopServo() {
    speakBlocking("stopping servo");
    releasePeer("servo");
    isServoActivated = false;
  }


  public static void main(String[] args) {
    try {

      LoggingFactory.init(Level.INFO);
      
      Runtime.main(new String[] { "--interactive", "--id", "admin", "-s", "intro", "Intro", "python", "Python" });
      
      // Arduino arduino = (Arduino)Runtime.start("arduino", "Arduino");
      WebGui webgui = (WebGui) Runtime.create("webgui", "WebGui");
      // webgui.setSsl(true);
      webgui.autoStartBrowser(false);
      webgui.setPort(8888);
      webgui.startService();


    } catch (Exception e) {
      log.error("main threw", e);
    }
  }

  
}