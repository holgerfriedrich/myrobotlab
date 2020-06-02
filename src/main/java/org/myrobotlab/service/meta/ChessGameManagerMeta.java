package org.myrobotlab.service.meta;

import org.myrobotlab.framework.Platform;
import org.myrobotlab.framework.ServiceType;
import org.myrobotlab.logging.LoggerFactory;
import org.slf4j.Logger;

public class ChessGameManagerMeta {
  public final static Logger log = LoggerFactory.getLogger(ChessGameManagerMeta.class);
  
  /**
   * This static method returns all the details of the class without it having
   * to be constructed. It has description, categories, dependencies, and peer
   * definitions.
   * 
   * @return ServiceType - returns all the data
   * 
   */
  static public ServiceType getMetaData() {

    ServiceType meta = new ServiceType("org.myrobotlab.service.ChessGameManager");
    Platform platform = Platform.getLocalInstance();
    meta.addDescription("manages multiple interfaces for a chess game");
    meta.addCategory("game");
    meta.addPeer("webgui", "WebGui", "webgui");
    meta.addPeer("serial", "Serial", "serial");
    meta.addPeer("speech", "MarySpeech", "speech");
    meta.setAvailable(false);
    return meta;
  }
  
  
}
