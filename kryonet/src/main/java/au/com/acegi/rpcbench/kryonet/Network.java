/*-
 * #%L
 * RPC Benchmark: Kryonet
 * %%
 * Copyright (C) 2016 - 2018 Acegi Technology Pty Limited
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package au.com.acegi.rpcbench.kryonet;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.EndPoint;

/**
 * Configuration common to both client and server.
 */
@SuppressWarnings(
    {"checkstyle:VisibilityModifier", "checkstyle:JavadocVariable"})
public final class Network {

  public static final int PORT = 54_555;

  private Network() {
  }

  public static void register(final EndPoint endPoint) {
    final Kryo kryo = endPoint.getKryo();
    kryo.setReferences(false);
    kryo.register(Ping.class);
    kryo.register(Pong.class);
    kryo.register(Size.class);
    kryo.register(Price.class);
  }

  /**
   * Ping message. Sent by client.
   */
  public static class Ping {

    public long timestamp;
  }

  /**
   * Pong message. Sent by server.
   */
  public static class Pong {

    public long timestamp;
  }

  /**
   * Price message. Sent by server.
   */
  public static class Price {

    public int ask;
    public int bid;
    public int iid;
    public long tod;
    public int trd;
    public int vol;
  }

  /**
   * Size message. Sent by client.
   */
  public static class Size {

    public int messages;
    public long tod;
  }

}
