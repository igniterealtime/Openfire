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
package asia.stampy.server.openfire;

import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.gateway.HostPort;
import asia.stampy.common.openfire.AbstractStampyOpenfireMessageGateway;

/**
 * The Class ServerOpenfireMessageGateway.
 */
@Resource
@StampyLibrary(libraryName = "stampy-OPENFIRE-client-server-RI")

public class ServerOpenfireMessageGateway extends AbstractStampyOpenfireMessageGateway
{
  private static final Logger Log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /*
   * (non-Javadoc)
   *
   * @see
   * asia.stampy.common.gateway.AbstractStampyMessageGateway#closeConnection
   * (asia.stampy.common.gateway.HostPort)
   */
  @Override
  public void closeConnection(HostPort hostPort) {
    getHandler().close(hostPort);
  }

  /*
   * (non-Javadoc)
   *
   * @see asia.stampy.common.gateway.AbstractStampyMessageGateway#connect()
   */
  @Override
  public void connect() throws Exception {

  }

  /*
   * (non-Javadoc)
   *
   * @see asia.stampy.common.gateway.AbstractStampyMessageGateway#shutdown()
   */
  @Override
  public void shutdown() throws Exception {
    Log.info("Stomp Server has been shut down");
  }

}
