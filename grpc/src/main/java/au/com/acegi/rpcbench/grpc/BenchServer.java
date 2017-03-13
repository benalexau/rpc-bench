/*-
 * #%L
 * RPC Benchmark: gRPC
 * %%
 * Copyright (C) 2016 - 2017 Acegi Technology Pty Limited
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
import static io.grpc.ServerInterceptors.intercept;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;

@SuppressWarnings("checkstyle:JavadocType")
public class BenchServer {

  private static final int PORT = 50_051;
  private io.grpc.Server server;

  @SuppressWarnings("checkstyle:UncommentedMain")
  public static void main(final String... args) throws InterruptedException,
                                                       IOException {
    final BenchServer server = new BenchServer();
    server.start();
    server.blockUntilShutdown();
  }

  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  @SuppressWarnings("PMD.DoNotUseThreads")
  private void start() throws IOException {
    server = NettyServerBuilder.forPort(PORT)
        .addService(intercept(new BenchImpl(), new ConnectionInterceptor()))
        .build()
        .start();

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        BenchServer.this.stop();
      }
    });
  }

  private void stop() {
    if (server != null) {
      server.shutdown();
    }
  }

  @SuppressWarnings("checkstyle:JavadocType")
  private class BenchImpl extends BenchGrpc.BenchImplBase {

    @Override
    public StreamObserver<Ping> pingPong(final StreamObserver<Pong> response) {

      return new StreamObserver<Ping>() {
        @Override
        public void onCompleted() {
          response.onCompleted();
        }

        @Override
        public void onError(final Throwable t) {
          throw new IllegalStateException(t);
        }

        @Override
        public void onNext(final Ping request) {
          final Pong.Builder bdr = Pong.newBuilder();
          bdr.setTimestamp(request.getTimestamp());
          response.onNext(bdr.build());
        }
      };
    }

    @Override
    public void priceStream(final Size request,
                            final StreamObserver<Price> response) {
      final long tod = request.getTod();
      for (int i = 0; i < request.getMessages(); i++) {
        final Price.Builder bdr = Price.newBuilder();
        bdr.setTod(tod);
        bdr.setIid(i);
        bdr.setBid(2);
        bdr.setAsk(3);
        bdr.setTrd(4);
        bdr.setVol(5);
        while (!ConnectionInterceptor.isReady()) {
          // busy spin
        }
        response.onNext(bdr.build());
      }
      response.onCompleted();
    }
  }
}
