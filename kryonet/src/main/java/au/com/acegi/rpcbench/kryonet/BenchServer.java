/*-
 * #%L
 * RPC Benchmark: Kryonet
 * %%
 * Copyright (C) 2016 - 2019 Acegi Technology Pty Limited
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

import au.com.acegi.rpcbench.kryonet.Network.Ping;
import au.com.acegi.rpcbench.kryonet.Network.Pong;
import au.com.acegi.rpcbench.kryonet.Network.Price;
import au.com.acegi.rpcbench.kryonet.Network.Size;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Listener.ThreadedListener;
import com.esotericsoftware.kryonet.Server;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SigInt;

@SuppressWarnings("checkstyle:JavadocType")
public final class BenchServer {

  private static final IdleStrategy IDLE = new BusySpinIdleStrategy();
  private static final int OBJECT_BUFFER = 2_048;
  private static final int WRITE_BUFFER = 1_024 * 16; // 16 KB

  private final AtomicBoolean running;
  private final Server server;

  public BenchServer() {
    running = new AtomicBoolean(true);
    SigInt.register(() -> running.set(false));
    server = new Server(WRITE_BUFFER, OBJECT_BUFFER);
  }

  @SuppressWarnings("checkstyle:UncommentedMain")
  public static void main(final String... args) throws IOException {
    final BenchServer svr = new BenchServer();
    svr.execute();
  }

  private void execute() throws IOException {
    Network.register(server);
    server.addListener(new PingListener());
    server.addListener(new ThreadedListener(new SizeListener()));
    server.bind(Network.PORT);
    server.start();
    while (running.get()) {
      // busy spin
      IDLE.idle();
    }
    server.stop();
  }

  private class PingListener extends Listener {

    @Override
    public void received(final Connection connection, final Object object) {
      if (object instanceof Ping) {
        final Ping ping = (Ping) object;
        final Pong pong = new Pong();
        pong.timestamp = ping.timestamp;
        connection.sendTCP(pong);
      }
    }
  }

  private class SizeListener extends Listener {

    @Override
    public void received(final Connection connection, final Object object) {
      if (object instanceof Size) {
        final Size size = (Size) object;
        final long tod = size.tod;
        final int msgs = size.messages;
        final Price price = new Price();
        for (int i = 0; i < msgs; i++) {
          price.tod = tod;
          price.iid = i;
          price.bid = 2;
          price.ask = 3;
          price.trd = 4;
          price.vol = 5;
          while (!connection.isIdle()) {
            // busy spin waiting for space in buffer
            IDLE.idle();
          }
          connection.sendTCP(price);
        }
      }
    }
  }

}
