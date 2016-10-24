/*-
 * #%L
 * RPC Benchmark: gRPC
 * %%
 * Copyright (C) 2016 Acegi Technology Pty Limited
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

package au.com.acegi.rpcbench.grpc;

import au.com.acegi.rpcbench.grpc.codecs.BenchGrpc;
import au.com.acegi.rpcbench.grpc.codecs.Ping;
import au.com.acegi.rpcbench.grpc.codecs.Pong;
import au.com.acegi.rpcbench.grpc.codecs.Price;
import au.com.acegi.rpcbench.grpc.codecs.Size;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import static java.lang.System.nanoTime;
import java.util.concurrent.CountDownLatch;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.concurrent.locks.LockSupport;
import org.HdrHistogram.Histogram;

@SuppressWarnings("checkstyle:JavadocType")
public final class BenchClient {

  private static final Histogram HISTOGRAM;
  private static final long PINGS_PER_SECOND = 10_000;
  private static final long SCHEDULE_INTERVAL_NS;
  private static final int TIMEOUT_PING = 600;
  private static final int TIMEOUT_PRICE = 14_400; // 4 hours
  private final ManagedChannel channel;
  private final BenchGrpc.BenchStub stub;

  static {
    SCHEDULE_INTERVAL_NS = SECONDS.toNanos(1) / PINGS_PER_SECOND;
    HISTOGRAM = new Histogram(SECONDS.toNanos(TIMEOUT_PRICE), 3);
  }

  public BenchClient(final String host, final int port) {
    channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true)
        .build();
    stub = BenchGrpc.newStub(channel);
  }

  @SuppressWarnings("checkstyle:UncommentedMain")
  public static void main(final String... args) throws InterruptedException,
                                                       FileNotFoundException {
    final BenchClient client = new BenchClient("localhost", 50_051);
    client.execute();
  }

  private static void writeResults(final String filename) throws
      FileNotFoundException {
    final File file = new File(filename);
    try (PrintStream ps = new PrintStream(file)) {
      HISTOGRAM.outputPercentileDistribution(ps, 5, 1000.0);
    }
  }

  private void execute() throws InterruptedException, FileNotFoundException {
    try {
      pingPong(100_000); // warm up
      HISTOGRAM.reset();
      LockSupport.parkNanos(SECONDS.toNanos(5));
      pingPong(1_000_000);
      writeResults("grpc-ping-pong-1M.txt");

      priceStream(100_000); // warm up
      HISTOGRAM.reset();
      LockSupport.parkNanos(SECONDS.toNanos(5));
      priceStream(100_000_000);
      writeResults("grpc-price-stream-100M.txt");
    } finally {
      shutdown();
    }
  }

  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  private void pingPong(final int messages) throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(messages);
    final StreamObserver<Pong> response = new StreamObserver<Pong>() {
      @Override
      public void onCompleted() {
        // ignored
      }

      @Override
      public void onError(final Throwable t) {
        throw new IllegalStateException("Remaining=" + latch.getCount(), t);
      }

      @Override
      public void onNext(final Pong value) {
        final long rtt = nanoTime() - value.getTimestamp();
        HISTOGRAM.recordValue(rtt);
        latch.countDown();
      }
    };
    final StreamObserver<Ping> ping
        = stub.withDeadlineAfter(TIMEOUT_PING, SECONDS).pingPong(response);
    final long start = nanoTime();
    long nextSendAt = start + SCHEDULE_INTERVAL_NS;
    for (int i = 0; i < messages; i++) {
      while (nanoTime() < nextSendAt) {
        // busy spin
      }
      final Ping request = Ping.newBuilder().setTimestamp(nextSendAt).build();
      nextSendAt += SCHEDULE_INTERVAL_NS;
      ping.onNext(request);
    }
    ping.onCompleted();
    if (!latch.await(TIMEOUT_PING, SECONDS)) {
      throw new IllegalStateException("Timed out");
    }
  }

  private void priceStream(final int messages) throws InterruptedException {
    final Size request = Size.newBuilder().setMessages(messages).setTod(
        nanoTime()).build();
    final CountDownLatch latch = new CountDownLatch(messages);
    final StreamObserver<Price> response = new StreamObserver<Price>() {
      @Override
      public void onCompleted() {
        latch.countDown();
      }

      @Override
      public void onError(final Throwable t) {
        throw new IllegalStateException("Remaining=" + latch.getCount(), t);
      }

      @Override
      public void onNext(final Price value) {
        final long rtt = nanoTime() - value.getTod();
        HISTOGRAM.recordValue(rtt);
        latch.countDown();
      }
    };
    stub.withDeadlineAfter(TIMEOUT_PRICE, SECONDS)
        .priceStream(request, response);
    if (!latch.await(TIMEOUT_PRICE, SECONDS)) {
      throw new IllegalStateException("Timeout; remaining=" + latch.getCount());
    }
  }

  private void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, SECONDS);
  }

}
