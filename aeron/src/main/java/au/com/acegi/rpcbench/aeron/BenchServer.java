/*-
 * #%L
 * RPC Benchmark: Aeron with SBE
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

package au.com.acegi.rpcbench.aeron;

import au.com.acegi.rpcbench.aeron.codecs.MessageHeaderDecoder;
import au.com.acegi.rpcbench.aeron.codecs.MessageHeaderEncoder;
import static au.com.acegi.rpcbench.aeron.codecs.MessageHeaderEncoder.ENCODED_LENGTH;
import au.com.acegi.rpcbench.aeron.codecs.PingDecoder;
import au.com.acegi.rpcbench.aeron.codecs.PongEncoder;
import au.com.acegi.rpcbench.aeron.codecs.PriceEncoder;
import au.com.acegi.rpcbench.aeron.codecs.SizeDecoder;
import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import static java.lang.System.setProperty;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BufferUtil.allocateDirectAligned;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SigInt;
import org.agrona.concurrent.UnsafeBuffer;
import static org.agrona.concurrent.UnsafeBuffer.DISABLE_BOUNDS_CHECKS_PROP_NAME;

@SuppressWarnings("checkstyle:JavadocType")
public final class BenchServer {

  private static final UnsafeBuffer BUFFER;
  private static final boolean EMBEDDED_MEDIA_DRIVER = false;
  private static final int FRAGMENT_LIMIT = 256;
  private static final MessageHeaderDecoder HDR_D;
  private static final MessageHeaderEncoder HDR_E;
  private static final IdleStrategy IDLE = new BusySpinIdleStrategy();
  private static final PingDecoder PING_D;
  private static final int PING_LEN;
  private static final PongEncoder PONG_E;
  private static final PriceEncoder PRICE_E;
  private static final int PRICE_LEN;
  private static final String REP_CHAN = Configuration.REP_CHANNEL;
  private static final int REP_STREAM_ID = Configuration.REP_STREAM_ID;
  private static final String REQ_CHAN = Configuration.REQ_CHANNEL;
  private static final int REQ_STREAM_ID = Configuration.REQ_STREAM_ID;
  private static final SizeDecoder SIZE_D;
  private final Aeron aeron;
  private final Aeron.Context ctx;
  private final MediaDriver driver;
  private final FragmentHandler fragmentHandler;
  private final Publication publication;
  private final AtomicBoolean running;
  private final Subscription subscription;

  static {
    setProperty(DISABLE_BOUNDS_CHECKS_PROP_NAME, "true");
    PING_LEN = ENCODED_LENGTH + PongEncoder.BLOCK_LENGTH;
    PRICE_LEN = ENCODED_LENGTH + PriceEncoder.BLOCK_LENGTH;
    final ByteBuffer bb = allocateDirectAligned(PRICE_LEN, CACHE_LINE_LENGTH);
    BUFFER = new UnsafeBuffer(bb);
    HDR_D = new MessageHeaderDecoder();
    HDR_E = new MessageHeaderEncoder();
    PING_D = new PingDecoder();
    PONG_E = new PongEncoder();
    PRICE_E = new PriceEncoder();
    SIZE_D = new SizeDecoder();
    HDR_E.wrap(BUFFER, 0);
    PONG_E.wrap(BUFFER, ENCODED_LENGTH);
    PRICE_E.wrap(BUFFER, ENCODED_LENGTH);
    HDR_E.schemaId(PongEncoder.SCHEMA_ID);
    HDR_E.version(PongEncoder.SCHEMA_VERSION);
  }

  @SuppressWarnings("PMD.NullAssignment")
  public BenchServer() {
    running = new AtomicBoolean(true);
    SigInt.register(() -> running.set(false));
    driver = EMBEDDED_MEDIA_DRIVER ? MediaDriver.launchEmbedded() : null;
    ctx = new Aeron.Context();
    if (EMBEDDED_MEDIA_DRIVER) {
      ctx.aeronDirectoryName(driver.aeronDirectoryName());
    }
    fragmentHandler = new FragmentAssembler(this::onMessage);
    aeron = Aeron.connect(ctx);
    publication = aeron.addPublication(REP_CHAN, REP_STREAM_ID);
    subscription = aeron.addSubscription(REQ_CHAN, REQ_STREAM_ID);
  }

  @SuppressWarnings("checkstyle:UncommentedMain")
  public static void main(final String... args) throws InterruptedException {
    final BenchServer svr = new BenchServer();
    svr.execute();
    svr.shutdown();
  }

  public void shutdown() throws InterruptedException {
    CloseHelper.quietClose(subscription);
    CloseHelper.quietClose(publication);
    CloseHelper.quietClose(aeron);
    CloseHelper.quietClose(ctx);
    CloseHelper.quietClose(driver);
  }

  private void execute() {
    final IdleStrategy idleStrategy = new BusySpinIdleStrategy();
    while (running.get()) {
      idleStrategy.idle(subscription.poll(fragmentHandler, FRAGMENT_LIMIT));
    }
  }

  @SuppressWarnings("PMD.UnusedFormalParameter")
  private void onMessage(final DirectBuffer buffer, final int offset,
                         final int length, final Header header) {
    final int msgOffset = ENCODED_LENGTH + offset;
    HDR_D.wrap(buffer, offset);
    switch (HDR_D.templateId()) {
      case PingDecoder.TEMPLATE_ID:
        onPing(buffer, msgOffset, HDR_D.blockLength(), HDR_D.version());
        break;
      case SizeDecoder.TEMPLATE_ID:
        onSize(buffer, msgOffset, HDR_D.blockLength(), HDR_D.version());
        break;
      default:
        throw new IllegalStateException("Unknown message template");
    }
  }

  private void onPing(final DirectBuffer buffer, final int offset,
                      final int actingBlockLength, final int actingVersion) {
    PING_D.wrap(buffer, offset, actingBlockLength, actingVersion);
    HDR_E.blockLength(PongEncoder.BLOCK_LENGTH);
    HDR_E.templateId(PongEncoder.TEMPLATE_ID);
    PONG_E.timestamp(PING_D.timestamp());
    sendBuffer(PING_LEN);
  }

  private void onSize(final DirectBuffer buffer, final int offset,
                      final int actingBlockLength, final int actingVersion) {
    SIZE_D.wrap(buffer, offset, actingBlockLength, actingVersion);
    HDR_E.blockLength(PriceEncoder.BLOCK_LENGTH);
    HDR_E.templateId(PriceEncoder.TEMPLATE_ID);
    final long tod = SIZE_D.tod();
    final int msgs = SIZE_D.messages();
    for (int i = 0; i < msgs; i++) {
      PRICE_E.tod(tod);
      PRICE_E.iid(i);
      PRICE_E.bid(2);
      PRICE_E.ask(3);
      PRICE_E.trd(4);
      PRICE_E.vol(5);
      sendBuffer(PRICE_LEN);
    }
  }

  @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
  private void sendBuffer(final int len) {
    if (publication.offer(BUFFER, 0, len) > 0L) {
      return;
    }

    IDLE.reset();

    while (publication.offer(BUFFER, 0, len) < 0L) {
      IDLE.idle();
    }
  }

}
