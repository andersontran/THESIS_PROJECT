// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.appinventor.shared.jsonp;

import java.io.Serializable;

/**
 * Class containing information needed for a JSONP connection.
 *
 * @author lizlooney@google.com (Liz Looney)
 */
public class JsonpConnectionInfo implements Serializable {
  private int port;
  private int secret;

  /**
   * Default constructor (for GWT RPC serialization only).
   * Unfortunately this will prevent any fields from being marked as final!
   */
  public JsonpConnectionInfo() {
  }

  /**
   * Constructs a JsonpConnectionInfo with the given port and secret.
   *
   * @param port the port at which the JSONP server is listening
   * @param secret the secret number that is required in all JSONP requests
   */
  public JsonpConnectionInfo(int port, int secret) {
    this.port = port;
    this.secret = secret;
  }

  /**
   * Returns the port at which the JSONP server is listening
   */
  public int getPort() {
    return port;
  }

  /**
   * Returns the secret number that is required in all JSONP requests.
   */
  public int getSecret() {
    return secret;
  }
}
