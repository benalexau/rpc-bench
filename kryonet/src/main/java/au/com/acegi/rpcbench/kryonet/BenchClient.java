/*-
 * #%L
 * RPC Benchmark: Kryonet
 * %%
 * Copyright (C) 2016 - 2020 Acegi Technology Pty Limited
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
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.concurrent.atomic.AtomicLong;
import static java.util.concurrent.locks.LockSupport.parkNanos;
import org.HdrHistogram.Histogram;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

@SuppressWarnings("checkstyle:JavadocType")
public final class BenchClient {

  private static final Histogram HISTOGRAM;
  private static final int HISTOGRAM_MAX_VAL_SEC = 14_400; // 4 hours
  private static final IdleStrategy IDLE = new BusySpinIdleStrategy();
  private static final AtomicLong PENDING = new AtomicLong();
  private static final long PINGS_PER_SECOND = 10_000;
  private static final long SCHEDULE_INTERVAL_NS;
  private final Client client;

  static {
    SCHEDULE_INTERVAL_NS = SECONDS.toNanos(1) / PINGS_PER_SECOND;
    HISTOGRAM = new Histogram(SECONDS.toNanos(HISTOGRAM_MAX_VAL_SEC), 3);
  }

  public BenchClient() {
    client = new Client();
  }

  @SuppressWarnings("checkstyle:UncommentedMain")
  public static void main(final String... args) throws FileNotFoundException,
                                                       IOException {
    final BenchClient client = new BenchClient();
    client.execute();
  }

  private static void writeResults(final String filename) throws
      FileNotFoundException {
    final File file = new File(filename);
    try (PrintStream ps = new PrintStream(file)) {
      HISTOGRAM.outputPercentileDistribution(ps, 5, 1000.0);
    }
  }

  private void execute() throws IOException {
    Network.register(client);
    client.addListener(new ListenerImpl());
    try {
      client.start();
      client.connect(5_000, "localhost", Network.PORT);
      pingPong(100_000); // warm up
      parkNanos(SECONDS.toNanos(5));
      HISTOGRAM.reset();
      pingPong(1_000_000);
      writeResults("kryonet-ping-pong-1M.txt");
      priceStream(100_000); // warm up
      HISTOGRAM.reset();
      parkNanos(SECONDS.toNanos(5));
      priceStream(100_000_000);
      writeResults("kryonet-price-stream-100M.txt");
    } finally {
      client.stop();
    }
  }

  private void pingPong(final int messages) {
    PENDING.set(messages);
    final long start = nanoTime();
    long nextSendAt = start + SCHEDULE_INTERVAL_NS;
    final Ping ping = new Ping();
    for (int i = 0; i < messages; i++) {
      while (nanoTime() < nextSendAt) {
        // busy spin
        IDLE.idle();
      }
      ping.timestamp = nextSendAt;
      nextSendAt += SCHEDULE_INTERVAL_NS;
      if (!client.isIdle()) {
        // busy spin waiting for send buffer space
        IDLE.idle();
      }
      client.sendTCP(ping);
    }
    while (PENDING.get() > 0) {
      // busy spin pending completion
      IDLE.idle();
    }
  }

  private void priceStream(final int messages) {
    PENDING.set(messages);
    final Size size = new Size();
    size.messages = messages;
    size.tod = nanoTime();
    client.sendTCP(size);

    while (PENDING.get() > 0) {
      // busy spin pending completion
      IDLE.idle();
    }
  }

  private class ListenerImpl extends Listener {

    @Override
    public void received(final Connection connection, final Object object) {
      if (object instanceof Pong) {
        onPong((Pong) object);
      } else if (object instanceof Price) {
        onPrice((Price) object);
      }
    }

    private void onPong(final Pong pong) {
      final long rtt = nanoTime() - pong.timestamp;
      HISTOGRAM.recordValue(rtt);
      PENDING.decrementAndGet();
    }

    private void onPrice(final Price price) {
      if (price.iid % 100_000 == 0) {
        final long rtt = nanoTime() - price.tod;
        HISTOGRAM.recordValue(rtt);
        PENDING.decrementAndGet();
      }
    }
  }

}
