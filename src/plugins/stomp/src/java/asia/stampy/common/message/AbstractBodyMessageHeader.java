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
package asia.stampy.common.message;

import asia.stampy.common.StampyLibrary;

/**
 * The Class AbstractBodyMessageHeader.
 */
@StampyLibrary(libraryName="stampy-core")
public class AbstractBodyMessageHeader extends AbstractMessageHeader {

  private static final long serialVersionUID = -4546439038775868974L;

  /** The Constant CONTENT_TYPE. */
  public static final String CONTENT_TYPE = "content-type";

  /**
   * Sets the content type.
   * 
   * @param mimeType
   *          the new content type
   */
  public void setContentType(String mimeType) {
    addHeader(CONTENT_TYPE, mimeType);
  }

  /**
   * Gets the content type.
   * 
   * @return the content type
   */
  public String getContentType() {
    return getHeaderValue(CONTENT_TYPE);
  }
}
