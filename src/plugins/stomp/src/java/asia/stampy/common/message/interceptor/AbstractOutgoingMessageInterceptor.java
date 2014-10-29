/*
 * Copyright (C) 2013 Burton Alexander
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 * 
 */
package asia.stampy.common.message.interceptor;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.gateway.AbstractStampyMessageGateway;
import asia.stampy.common.gateway.HostPort;
import asia.stampy.common.message.StampyMessage;

/**
 * The Class AbstractOutgoingMessageInterceptor.
 */
@StampyLibrary(libraryName="stampy-client-server")
public abstract class AbstractOutgoingMessageInterceptor<SVR extends AbstractStampyMessageGateway> implements
    StampyOutgoingMessageInterceptor {

  private SVR gateway;

  /*
   * (non-Javadoc)
   * 
   * @see
   * asia.stampy.common.message.interceptor.StampyOutgoingMessageInterceptor
   * #interceptMessage(asia.stampy.common.message.StampyMessage)
   */
  @Override
  public void interceptMessage(StampyMessage<?> message) throws InterceptException {
    for (HostPort hostPort : getGateway().getConnectedHostPorts()) {
      interceptMessage(message, hostPort);
    }
  }

  /**
   * Gets the gateway.
   * 
   * @return the gateway
   */
  public SVR getGateway() {
    return gateway;
  }

  /**
   * Sets the gateway.
   * 
   * @param gateway
   *          the new gateway
   */
  public void setGateway(SVR gateway) {
    this.gateway = gateway;
  }

}
