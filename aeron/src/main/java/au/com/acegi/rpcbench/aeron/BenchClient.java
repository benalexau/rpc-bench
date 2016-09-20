/*-
 * #%L
 * RPC Benchmark: Aeron with SBE
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

package au.com.acegi.rpcbench.aeron;

import au.com.acegi.rpcbench.aeron.codecs.MessageHeaderDecoder;
import au.com.acegi.rpcbench.aeron.codecs.MessageHeaderEncoder;
import au.com.acegi.rpcbench.aeron.codecs.PingEncoder;
import au.com.acegi.rpcbench.aeron.codecs.PongDecoder;
import au.com.acegi.rpcbench.aeron.codecs.PriceDecoder;
import au.com.acegi.rpcbench.aeron.codecs.SizeEncoder;
import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import static java.lang.System.nanoTime;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.concurrent.atomic.AtomicLong;
import org.HdrHistogram.Histogram;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BufferUtil.allocateDirectAligned;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;

@SuppressWarnings("checkstyle:JavadocType")
public final class BenchClient {

  private static final UnsafeBuffer BUFFER;
  private static final int BUFF_SIZE;
  private static final boolean EMBEDDED_MEDIA_DRIVER = false;
  private static final int FRAGMENT_LIMIT = 256;
  private static final MessageHeaderDecoder HDR_D;
  private static final MessageHeaderEncoder HDR_E;
  private static final Histogram HISTOGRAM;
  private static final int HISTOGRAM_MAX_VAL_SEC = 60;
  private static final IdleStrategy IDLE = new BusySpinIdleStrategy();
  private static final CountDownLatch LATCH = new CountDownLatch(1);
  private static final AtomicLong PENDING = new AtomicLong();
  private static final PingEncoder PING_E;
  private static final PongDecoder POND_D;
  private static final PriceDecoder PRICE_D;
  private static final String REP_CHAN = Configuration.REP_CHANNEL;
  private static final int REP_STREAM_ID = Configuration.REP_STREAM_ID;
  private static final String REQ_CHAN = Configuration.REQ_CHANNEL;
  private static final int REQ_STREAM_ID = Configuration.REQ_STREAM_ID;
  private static final SizeEncoder SIZE_E;
  private final Aeron aeron;
  private final Aeron.Context ctx;
  private final MediaDriver driver;
  private final FragmentHandler fragmentHandler;
  private final Publication publication;
  private final Subscription subscription;

  static {
    BUFF_SIZE = MessageHeaderEncoder.ENCODED_LENGTH + SizeEncoder.BLOCK_LENGTH;
    final ByteBuffer bb = allocateDirectAligned(BUFF_SIZE, CACHE_LINE_LENGTH);
    BUFFER = new UnsafeBuffer(bb);
    HISTOGRAM = new Histogram(SECONDS.toNanos(HISTOGRAM_MAX_VAL_SEC), 3);
    HDR_D = new MessageHeaderDecoder();
    HDR_E = new MessageHeaderEncoder();
    PING_E = new PingEncoder();
    POND_D = new PongDecoder();
    PRICE_D = new PriceDecoder();
    SIZE_E = new SizeEncoder();
    HDR_E.wrap(BUFFER, 0);
    PING_E.wrap(BUFFER, MessageHeaderEncoder.ENCODED_LENGTH);
    SIZE_E.wrap(BUFFER, MessageHeaderEncoder.ENCODED_LENGTH);
    HDR_E.schemaId(PingEncoder.SCHEMA_ID);
    HDR_E.version(PingEncoder.SCHEMA_VERSION);
  }

  public BenchClient() {
    driver = EMBEDDED_MEDIA_DRIVER ? MediaDriver.launchEmbedded() : null;
    ctx = new Aeron.Context().availableImageHandler(this::imageHandler);
    if (EMBEDDED_MEDIA_DRIVER) {
      ctx.aeronDirectoryName(driver.aeronDirectoryName());
    }
    fragmentHandler = new FragmentAssembler(this::onMessage);
    aeron = Aeron.connect(ctx);
    publication = aeron.addPublication(REQ_CHAN, REQ_STREAM_ID);
    subscription = aeron.addSubscription(REP_CHAN, REP_STREAM_ID);
  }

  @SuppressWarnings("checkstyle:UncommentedMain")
  public static void main(final String... args) throws InterruptedException,
                                                       FileNotFoundException {
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

  public void shutdown() throws InterruptedException {
    CloseHelper.quietClose(subscription);
    CloseHelper.quietClose(publication);
    CloseHelper.quietClose(aeron);
    CloseHelper.quietClose(ctx);
    CloseHelper.quietClose(driver);
  }

  private void execute() throws InterruptedException, FileNotFoundException {
    try {
      if (!LATCH.await(5, SECONDS)) {
        throw new IllegalStateException("Couldn't connect to server");
      }
      pingPong(100_000); // warm up
      HISTOGRAM.reset();
      pingPong(1_000_000);
      writeResults("aeron-ping-pong-1M.txt");

      priceStream(100_000); // warm up
      HISTOGRAM.reset();
      priceStream(100_000_000);
      writeResults("aeron-price-stream-100M.txt");
    } finally {
      shutdown();
    }
  }

  private void imageHandler(final Image image) {
    final Subscription sub = image.subscription();
    if (REP_STREAM_ID == sub.streamId() && REP_CHAN.equals(sub.channel())) {
      LATCH.countDown();
    }
  }

  @SuppressWarnings("PMD.UnusedFormalParameter")
  private void onMessage(final DirectBuffer buffer, final int offset,
                         final int length, final Header header) {
    final int msgOffset = MessageHeaderDecoder.ENCODED_LENGTH + offset;
    HDR_D.wrap(buffer, offset);
    switch (HDR_D.templateId()) {
      case PongDecoder.TEMPLATE_ID:
        onPong(buffer, msgOffset, HDR_D.blockLength(), HDR_D.version());
        break;
      case PriceDecoder.TEMPLATE_ID:
        onPrice(buffer, msgOffset, HDR_D.blockLength(), HDR_D.version());
        break;
      default:
        throw new IllegalStateException("Unknown message template");
    }
  }

  private void onPong(final DirectBuffer buffer, final int offset,
                      final int actingBlockLength, final int actingVersion) {
    POND_D.wrap(buffer, offset, actingBlockLength, actingVersion);
    final long rtt = nanoTime() - POND_D.timestamp();
    HISTOGRAM.recordValue(rtt);
  }

  private void onPrice(final DirectBuffer buffer, final int offset,
                       final int actingBlockLength, final int actingVersion) {
    PRICE_D.wrap(buffer, offset, actingBlockLength, actingVersion);
    final long rtt = nanoTime() - PRICE_D.tod();
    HISTOGRAM.recordValue(rtt);
    PENDING.decrementAndGet();
  }

  private void pingPong(final int messages) throws InterruptedException {
    HDR_E.blockLength(PingEncoder.BLOCK_LENGTH);
    HDR_E.templateId(PingEncoder.TEMPLATE_ID);
    for (int i = 0; i < messages; i++) {
      do {
        PING_E.timestamp(nanoTime());
      } while (publication.offer(BUFFER, 0, BUFF_SIZE) < 0L);

      IDLE.reset();
      while (subscription.poll(fragmentHandler, FRAGMENT_LIMIT) <= 0) {
        IDLE.idle();
      }
    }
  }

  private void priceStream(final int messages) throws InterruptedException {
    PENDING.set(messages);
    HDR_E.blockLength(SizeEncoder.BLOCK_LENGTH);
    HDR_E.templateId(SizeEncoder.TEMPLATE_ID);
    SIZE_E.messages(messages);
    SIZE_E.tod(nanoTime());
    while (publication.offer(BUFFER, 0, BUFF_SIZE) < 0L) {
      IDLE.idle();
    }
    IDLE.reset();
    while (PENDING.get() > 0) {
      while (subscription.poll(fragmentHandler, FRAGMENT_LIMIT) <= 0) {
        IDLE.idle();
      }
    }
  }
}
