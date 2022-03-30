package org.myrobotlab.service.config;

import java.util.Map;

public class SpeechSynthesisConfig extends ServiceConfig {

  public boolean mute;
  public boolean blocking;
  public String[] speechRecognizers;
  public Map<String, String> substitutions;
  public String voice;

}