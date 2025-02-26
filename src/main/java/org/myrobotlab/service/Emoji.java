package org.myrobotlab.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.myrobotlab.framework.Service;
import org.myrobotlab.fsm.api.Event;
import org.myrobotlab.fsm.api.EventHandler;
import org.myrobotlab.fsm.api.SimpleEvent;
import org.myrobotlab.fsm.api.State;
import org.myrobotlab.fsm.core.SimpleTransition;
import org.myrobotlab.io.FileIO;
import org.myrobotlab.logging.Level;
import org.myrobotlab.logging.LoggerFactory;
import org.myrobotlab.logging.LoggingFactory;
import org.myrobotlab.service.config.EmojiConfig;
import org.myrobotlab.service.data.ImageData;
import org.myrobotlab.service.interfaces.ImagePublisher;
import org.myrobotlab.service.interfaces.TextListener;
import org.myrobotlab.service.interfaces.TextPublisher;
import org.slf4j.Logger;

// emotionListener
// Links
// - http://googleemotionalindex.com/
public class Emoji extends Service implements TextListener, EventHandler, ImagePublisher {

  private static final long serialVersionUID = 1L;

  public final static Logger log = LoggerFactory.getLogger(Emoji.class);

  // transient ImageDisplay display = null;

  transient HttpClient http = null;

  State lastState = null;

  final Set<Integer> validSize = new HashSet<>();

  static public class EmojiData {
    String name;
    String src;
    String unicode;
    String[] description;
    String emoticon;
  }

  public Emoji(String n, String id) {
    super(n, id);
    validSize.add(32);
    validSize.add(72);
    validSize.add(128);
    validSize.add(512);
  }

  /**
   * digest emoji-map.csv for keys to code for emoji
   */
  public void addEmojiMap() {
    try {
      Map<String, String> map = ((EmojiConfig) config).map;
      if (map.isEmpty()) {
        String emojiMap = getResourceDir() + fs + "emoji-map.csv";
        File check = new File(emojiMap);
        if (check.exists()) {
          List<String> allLines = Files.readAllLines(Paths.get(emojiMap));
          for (String line : allLines) {
            String[] parts = line.split(",");
            if (parts.length == 2) {
              String code = parts[0];// parts[0].replace("+", "").toLowerCase();
              String description = parts[1].toLowerCase();
              addEmoji(description, code);
            }
          }
        } else {
          log.info("no emoji-map.csv");
        }
      }
    } catch (Exception e) {
      error(e);
    }

  }

  public void addEmoji(String keyword, String unicode) {
    log.info("emoji {}:{}", keyword, unicode);
    ((EmojiConfig) config).map.put(keyword, unicode);
  }

  public void clearEmojis() {
    ((EmojiConfig) config).map.clear();
  }

  public void startService() {
    super.startService();

    // FIXME - send default fullscreen always on top minAutoSize to display ?
    // display = (ImageDisplay) startPeer("display");
    http = (HttpClient) startPeer("http");

    addEmojiMap();
  }

  public void display(String source) {
    try {

      String cacheDir = getEmojiCacheDir();

      log.info("display source {} fullscreen {}", source);
      String filename = null;

      File check = new File(cacheDir);
      if (!check.exists()) {
        check.mkdirs();
      }

      Map<String, String> map = ((EmojiConfig) config).map;

      // check for keyword
      if (map.containsKey(source)) {
        String unicodeFileName = cacheDir + File.separator + map.get(source) + ".png";
        if (!new File(unicodeFileName).exists()) {
          try {
            String fetchCode = map.get(source).replace("+", "").toLowerCase();
            String url = ((EmojiConfig) config).emojiSourceUrlTemplate.replace("{size}", "" + getSize()).replace("{code}", fetchCode).replace("{CODE}", source);
            byte[] bytes = http.getBytes(url);
            FileIO.toFile(unicodeFileName, bytes);
          } catch (Exception e) {
            error(e);
          }
        }

        filename = unicodeFileName;
      }

      if (filename == null) {
        String unicodeFileName = cacheDir + File.separator + source + ".png";
        if (new File(unicodeFileName).exists()) {
          filename = unicodeFileName;
        }
      }

      // hail mary - just assign what its been told to display
      if (filename == null) {
        filename = source;
      }

      // FIXME - add by sending
      // common display attributes
//      display.setAlwaysOnTop(false);
//      display.setFullScreen(true);
//      display.setColor("#000");
//      display.setColor("#000000");
//      display.display(filename);
      
      ImageData img = new ImageData();
      img.name = source;
      img.src = filename;
      img.source = getName();
      
      invoke("publishImage", img);
      
    } catch (Exception e) {
      log.error("displayFullScreen threw", e);
    }
  }
  

  public int getSize() {
    int size = 128;
    int configSize = ((EmojiConfig) config).size;
    if (validSize.contains(configSize)) {
      size = configSize;
    }
    return size;
  }

  public String getEmojiCacheDir() {
    return getDataDir() + File.separator + getSize() + "px";
  }

  @Override
  public void onText(String text) {
    // FIXME - look for emotional words

  }

  // FIXME - publish events if desired...
  @Override
  public void handleEvent(Event event) throws Exception {
    log.info("handleEvent {}", event);
    SimpleEvent se = (SimpleEvent) event;
    SimpleTransition transition = (SimpleTransition) se.getTransition();
    EmojiData emoji = new EmojiData();
    emoji.name = transition.getTargetState().getName();
    emoji.unicode = ((EmojiConfig) config).map.get(emoji.name);
    invoke("publishEmoji", emoji);
    display(transition.getTargetState().getName());
    // emotionalState.getCurrentState().getName();
  }

  public void publishEmoji(EmojiData emoji) {
    log.info("publishEmoji {}", emoji);
  }

  public static void main(String[] args) {
    try {

      // excellent resource
      // https://emojipedia.org/shocked-face-with-exploding-head/ (descriptions,
      // unicode)
      // https://emojipedia.org/freezing-face/
      // https://unicode.org/emoji/charts/full-emoji-list.html#1f600
      // https://apps.timwhitlock.info/emoji/tables/unicode#block-1-emoticons
      // https://unicode.org/emoji/charts/full-emoji-list.html

      // https://commons.wikimedia.org/wiki/Emoji

      // scan text for emotional words - addEmotionWordPair(happy 1f609) ...
      LoggingFactory.init(Level.WARN);

      Runtime.startConfig("emoji-display-2");
      
      // Runtime.startConfig("emoji-display-1");
      // Runtime.saveConfig("emoji-display-2");

      Emoji emoji = (Emoji) Runtime.start("emoji", "Emoji");    
      ImageDisplay display = (ImageDisplay) Runtime.start("display", "ImageDisplay");
      emoji.attachImageListener(display);
      
      emoji.display("grinning face");
      sleep(800);
      emoji.display("tired face");
      sleep(800);
      emoji.display("skull");
      sleep(800);
      emoji.display("speak-no-evil monkey");
      sleep(800);
      emoji.display("postal horn");
      sleep(800);
      emoji.display("radio");
      sleep(800);
      emoji.display("video game");
      sleep(800);
      emoji.display("telephone receiver");
      sleep(800);
      emoji.display("ghost");
      sleep(800);
      emoji.display("extraterrestrial alien");
      sleep(800);
      emoji.display("electric light bulb");
      sleep(800);
      display.closeAll();
      
      
      // Runtime.saveConfig("emoji-display-1");
//      ImageDisplay image = emoji.getDisplay();
//      Runtime.saveConfig("emoji-1");
//      image.closeAll();
      // FiniteStateMachine fsm = (FiniteStateMachine) emoji.getPeer("fsm");
      //
      // // create a new fsm with 4 states
      // fsm.setStates("neutral", "ill", "sick", "vomiting");
      // // fsm.setState("neutral");
      //
      // // add the ill-event transitions
      // fsm.addTransition("neutral", "ill-event", "ill");
      // fsm.addTransition("ill", "ill-event", "sick");
      // fsm.addTransition("sick", "ill-event", "vomiting");
      //
      // // add the clear-event transitions
      // fsm.addTransition("ill", "clear-event", "neutral");
      // fsm.addTransition("sick", "clear-event", "ill");
      // fsm.addTransition("vomiting", "clear-event", "sick");
      //
      // fsm.subscribe("fsm", "publishState");

      // emoji.fire("ill-event");
      // emoji.fire("clear-event");
      // Service.sleep(2000);
      // emoji.fire("ill-event");
      // Service.sleep(2000);
      // emoji.fire("ill-event");
      // Service.sleep(2000);
      // emoji.fire("ill-event");

      // State state = emoji.getCurrentState();
      // emoji.display(state.getName());

      // Runtime.release("emoji");

      log.info("here");

      /**
       * <pre>
       * State state = emoji.getCurrentState();
       * emoji.display(state.getName());
       * log.info("state {}", state);
       * 
       * emoji.fire("ill-event");
       * log.info("state {}", emoji.getCurrentState());
       * 
       * emoji.fire("ill-event");
       * log.info("state {}", emoji.getCurrentState());
       * 
       * emoji.fire("ill-event");
       * log.info("state {}", emoji.getCurrentState());
       * 
       * emoji.fire("ill-event");
       * log.info("state {}", emoji.getCurrentState());
       * 
       * emoji.fire("ill-event");
       * log.info("state {}", emoji.getCurrentState());
       * 
       * // emoji.cacheEmojis(); - FIXME - fix caching
       * for (int i = 0; i < 100; ++i) {
       *   emoji.display("1f610");
       *   Service.sleep(1000);
       *   emoji.display("1f627");
       *   Service.sleep(1000);
       *   emoji.display("1f628");
       *   Service.sleep(1000);
       *   emoji.display("1f631");
       *   Service.sleep(1000);
       *   emoji.display("1f92e");
       *   Service.sleep(1000);
       *   emoji.display("1f610");
       *   Service.sleep(1000);
       *   emoji.display("2639");
       *   Service.sleep(1000);
       *   emoji.display("2639");
       *   Service.sleep(1000);
       *   emoji.display("1f621");
       *   Service.sleep(1000);
       *   emoji.display("1f620");
       *   Service.sleep(1000);
       *   // FIXME - underlying does not use httpclient - so redirect FAILS !!!
       *   // -
       *   // should use httpclient & cache !!!
       *   // FIXME - url or file vs unicode addEmoji("happy",
       *   // "https://hips.hearstapps.com/hmg-prod.s3.amazonaws.com/images/vollmer1hr-1534611173.jpg")
       *   emoji.display("https://hips.hearstapps.com/hmg-prod.s3.amazonaws.com/images/vollmer1hr-1534611173.jpg");
       *   Service.sleep(1000);
       * 
       * }
       * </pre>
       */

      // FIXME cache file
      /*
       * for (int i = 609; i < 999; ++i) { // emoji.display("1f609");
       * 
       * emoji.display(String.format("1f%03d", i)); }
       */

    } catch (Exception e) {
      log.error("main threw", e);
    }
  }

  @Override
  public void attachTextPublisher(TextPublisher service) {
    if (service == null) {
      log.warn("{}.attachTextPublisher(null)");
      return;
    }
    subscribe(service.getName(), "publishText");
  }

  @Override
  public ImageData publishImage(ImageData data) {
    return data;
  }

}
