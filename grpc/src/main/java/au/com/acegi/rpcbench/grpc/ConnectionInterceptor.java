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

import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * Interceptor to mitigate out of memory issues.
 */
@SuppressWarnings("PMD.UseUtilityClass")
public final class ConnectionInterceptor implements ServerInterceptor {

  private static final ThreadLocal<ServerCall<?, ?>> TL = new ThreadLocal<>();

  public static boolean isReady() {
    final ServerCall<?, ?> call = TL.get();
    if (call == null) {
      throw new IllegalStateException("No ServerCall thread local");
    }
    return call.isReady();
  }

  @SuppressWarnings("checkstyle:MethodTypeParameterName")
  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call, final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {
    TL.set(call);

    return next.startCall(new SimpleForwardingServerCall<ReqT, RespT>(call) {
      @Override
      public void close(final Status status, final Metadata trailers) {
        super.close(status, trailers);
        TL.remove();
      }

    }, headers);
  }

}
