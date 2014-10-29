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
package asia.stampy.server.openfire.subscription;

import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asia.stampy.common.StampyLibrary;
import asia.stampy.common.gateway.HostPort;
import asia.stampy.common.message.StompMessageType;
import asia.stampy.server.listener.subscription.AbstractAcknowledgementListenerAndInterceptor;
import asia.stampy.server.listener.subscription.StampyAcknowledgementHandler;
import asia.stampy.server.openfire.ServerOpenfireMessageGateway;

/**
 * This class assists in the publication of {@link StompMessageType#MESSAGE}
 * messages for a subscription. If confirmation of the publication is requested
 * a timer is created to await receipt of the confirmation, and the appropriate
 * methods of the {@link StampyAcknowledgementHandler} implementation are
 * invoked.
 */
@Resource
@StampyLibrary(libraryName = "stampy-OPENFIRE-client-server-RI")

public class OpenfireAcknowledgementListenerAndInterceptor extends AbstractAcknowledgementListenerAndInterceptor<ServerOpenfireMessageGateway>
{
  private static final Logger Log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /*
   * (non-Javadoc)
   *
   * @see asia.stampy.server.listener.subscription.
   * AbstractAcknowledgementListenerAndInterceptor#ensureCleanup()
   */
  @Override
  protected void ensureCleanup() {

  }

}
