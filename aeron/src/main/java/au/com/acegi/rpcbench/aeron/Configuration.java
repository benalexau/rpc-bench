/*-
 * #%L
 * RPC Benchmark: Aeron with SBE
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

package au.com.acegi.rpcbench.aeron;

@SuppressWarnings({"checkstyle:JavadocType", "checkstyle:JavadocVariable"})
public final class Configuration {

  public static final String REP_CHANNEL = "aeron:udp?endpoint=localhost:40124";
  public static final int REP_STREAM_ID = 10;
  public static final String REQ_CHANNEL = "aeron:udp?endpoint=localhost:40123";
  public static final int REQ_STREAM_ID = 10;

  private Configuration() {
  }
}
