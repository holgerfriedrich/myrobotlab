package org.myrobotlab.service.meta;

import org.myrobotlab.logging.LoggerFactory;
import org.myrobotlab.service.meta.abstracts.MetaData;
import org.slf4j.Logger;

public class WatchDogTimerMeta extends MetaData {
  private static final long serialVersionUID = 1L;
  public final static Logger log = LoggerFactory.getLogger(WatchDogTimerMeta.class);

  /**
   * This class is contains all the meta data details of a service. It's peers,
   * dependencies, and all other meta data related to the service.
   */
  public WatchDogTimerMeta() {

    addDescription("used as a general template");
    setAvailable(true); // false if you do not want it viewable in a gui
    // add dependency if necessary
    // addDependency("org.coolproject", "1.0.0");
    addCategory("safety");

  }

}
