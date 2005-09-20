/**
 * 
 */
package org.jrobin.annotations;

/**
 * @author Noah Campbell
 * @version 1.0
 */
public enum SourceType {
    

   /**
    *  </dd><dt><strong><a name="item_COUNTER"><strong>COUNTER</strong></a></strong><br>
    * </dt><dd>is for continuous incrementing counters like the InOctets counter in a router. The <strong>COUNTER</strong>
    * data source assumes that the counter never decreases, except when a
    * counter overflows. The update function takes the overflow into account.
    * The counter is stored as a per-second rate. When the counter overflows,
    * RRDtool checks if the overflow happened at the 32bit or 64bit border
    * and acts accordingly by adding an appropriate value to the result. <p></p>
    */ 
    COUNTER, 
    
    /**
     * <dt><strong><a name="item_GAUGE"><strong>GAUGE</strong></a></strong><br>
     * </dt><dd>is for things like temperatures or number of people in a room or value of a RedHat share. 
     * <p></p>
     */
    GAUGE, 
    
    /** 
     * </dd><dt><strong><a name="item_DERIVE"><strong>DERIVE</strong></a></strong><br>
     * </dt><dd>will store the derivative of the line going from the last to
     * the current value of the data source. This can be useful for gauges,
     * for example, to measure the rate of people entering or leaving a room.
     * Internally, derive works exaclty like COUNTER but without overflow
     * checks. So if your counter does not reset at 32 or 64 bit you might
     * want to use DERIVE and combine it with a MIN value of 0. <p></p>
     */
     DERIVE, 
    
     /** 
      * </dd><dt><strong><a name="item_ABSOLUTE"><strong>ABSOLUTE</strong></a></strong><br>
      * </dt><dd>is for counters which get reset upon reading. This is used for
      * fast counters which tend to overflow. So instead of reading them
      * normally you reset them after every read to make sure you have a
      * maximal time available before the next overflow. Another usage is for
      * things you count like number of messages since the last update.
      */
    ABSOLUTE;
}
