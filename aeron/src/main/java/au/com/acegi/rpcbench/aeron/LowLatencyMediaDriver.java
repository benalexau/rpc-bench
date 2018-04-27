/*-
 * #%L
 * RPC Benchmark: Aeron with SBE
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

package au.com.acegi.rpcbench.aeron;

import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import static java.lang.System.setProperty;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.SigIntBarrier;
import static org.agrona.concurrent.UnsafeBuffer.DISABLE_BOUNDS_CHECKS_PROP_NAME;

@SuppressWarnings("checkstyle:JavadocType")
public final class LowLatencyMediaDriver {

  private LowLatencyMediaDriver() {
  }

  @SuppressWarnings("checkstyle:UncommentedMain")
  public static void main(final String... args) {
    MediaDriver.loadPropertiesFiles(args);

    setProperty(DISABLE_BOUNDS_CHECKS_PROP_NAME, "true");
    setProperty("aeron.mtu.length", "16384");
    setProperty("aeron.socket.so_sndbuf", "2097152");
    setProperty("aeron.socket.so_rcvbuf", "2097152");
    setProperty("aeron.rcv.initial.window.length", "2097152");

    final MediaDriver.Context ctx = new MediaDriver.Context()
        .threadingMode(ThreadingMode.DEDICATED)
        .dirsDeleteOnStart(true)
        .termBufferSparseFile(false)
        .conductorIdleStrategy(new BusySpinIdleStrategy())
        .receiverIdleStrategy(new BusySpinIdleStrategy())
        .senderIdleStrategy(new BusySpinIdleStrategy());

    try (MediaDriver ignored = MediaDriver.launch(ctx)) {
      new SigIntBarrier().await();

    }
  }
}
