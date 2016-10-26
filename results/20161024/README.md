# Benchmark Results: 24 October 2016

### Introduction

This report updates a [prior report](https://github.com/benalexau/rpc-bench/blob/master/results/20160920/README.md).
Please refer to the prior report for background information.

[KryoNet](https://github.com/EsotericSoftware/kryonet) was also added in this
version of the report. KryoNet is a JVM-only solution that uses its own TCP or
UDP connections and the fast Kryo serializer.

### Benchmark Update

#### Ping-Pong

As a result of [Issue #1](https://github.com/benalexau/rpc-bench/issues/1), the
ping-pong benchmark was redesigned. The main changes are:

1. 10,000 pings are sent per second (ie 100,000 nanoseconds between each ping)
2. Time is measured from the moment a ping was scheduled to be sent
3. Different threads are used for sending and receiving
4. gRPC was changed to use a bidirectional stream (not request-response)
5. Aeron message payloads were reduced to that necessary for each sent message
6. Aeron was configured to not perform bounds checking via its buffer
7. Addition of KryoNet to the benchmark

#### Price Stream

The following changes applied to the price stream benchmark:

1. Aeron was configured to not perform bounds checking via its buffer
2. Addition of KryoNet to the benchmark

#### KryoNet Configuration

While KryoNet supports TCP and UDP, only TCP offers guarantees around message
ordering and retry. Confining the test to TCP therefore reflects the quality of
service guarantees made by Aeron (which uses UDP but with automatic management
of delivery order and retry) and gRPC (which uses HTTP 2 transport).

For the KryoNet server, the ping-pong responder ran in the same thread as the
KryoNet server. The price stream responder ran in a different thread given its
long execution duration blocks the KryoNet server thread and disconnects the
client.

#### Machine Configuration

The following changes were made:

1. Aeron was upgraded to version 1.0.2
2. Protocol Buffers was upgraded to version 3.1.0
3. Linux was upgraded to version 4.7.4
4. KryoNet was added with version 2.22.0-RC1 and Kryo version 4.0.0

### Benchmark Reproduction

This benchmark was run with Git revision 317a91a9156cf546b97281ba7fe850ce955533dd.

### Results

All values are in microseconds.

#### Ping-Pong

![img](ping-pong.png)

| Percentile | [gRPC](grpc-ping-pong-1M.txt) | [KryoNet](kryonet-ping-pong-1M.txt) | [Aeron](aeron-ping-pong-1M.txt) |
| ---------- | ------: | ----: | --: |
| 0.00       | 37      | 11    | 3   |
| 0.10       | 48      | 17    | 4   |
| 0.20       | 51      | 18    | 4   |
| 0.30       | 54      | 18    | 4   |
| 0.40       | 56      | 18    | 4   |
| 0.50       | 59      | 19    | 4   |
| 0.60       | 62      | 20    | 4   |
| 0.70       | 65      | 21    | 5   |
| 0.80       | 69      | 22    | 5   |
| 0.90       | 76      | 24    | 6   |
| 0.95       | 81      | 26    | 6   |
| 0.99       | 130     | 33    | 8   |
| 0.999      | 4,452   | 61    | 13  |
| 1.00       | 14,532  | 2,770 | 895 |

Despite ping-pong benchmark changes that would assist gRPC (eg using a single
bidirectional stream, throttling requests to a level that gRPC can easily
accommodate on this hardware based on the price stream benchmark etc), Aeron
continues to provide much lower and more stable latency.

#### Price Stream

![img](price-stream.png)

| Percentile | [gRPC](grpc-price-stream-100M.txt) | [KryoNet](kryonet-price-stream-100M.txt) | [Aeron](aeron-price-stream-100M.txt) |
| ---------- | ------------: | ----------: | ----------: |
| 0.00       | 5,570         |       1,589 | 136         |
| 0.10       | 107,374,182   |  38,990,249 | 682,622     |
| 0.20       | 211,392,921   |  77,510,737 | 1,214,251   |
| 0.30       | 319,975,063   | 115,427,246 | 1,719,664   |
| 0.40       | 432,717,955   | 155,155,693 | 2,222,981   |
| 0.50       | 546,266,152   | 192,468,221 | 2,736,783   |
| 0.60       | 603,442,905   | 228,707,008 | 3,244,294   |
| 0.70       | 658,740,609   | 265,616,883 | 3,743,416   |
| 0.80       | 770,409,758   | 301,184,581 | 4,236,247   |
| 0.90       | 992,137,445   | 338,765,545 | 4,739,563   |
| 0.95       | 1,048,508,891 | 355,408,543 | 4,987,027   |
| 0.99       | 1,094,679,789 | 370,977,800 | 5,188,354   |
| 0.999      | 1,104,880,336 | 374,467,461 | 5,230,297   |
| 1.00       | 1,105,954,078 | 374,735,896 | 5,234,491   |

Aeron delivered over 19 million messages per second, completing the transfer
211 times faster than gRPC and 71 times faster than KryoNet. This is a material
improvement since the prior benchmark report and is mostly due to buffer bounds
checks being disabled.

Aeron's user-defined throughput was 44 bytes per message, consisting of an 8
byte user reserved value field (not used in the benchmark, but sent anyway), an
8 byte SBE header, and a 28 byte SBE price message. As such Aeron delivered 801
megabytes of user-defined content per second.

### Conclusion

As was seen in the prior benchmark, Aeron continues to deliver exceptional
performance and latency outcomes. The prior report's qualifier around the
very different focuses of the stacks is again reiterated.

If you are interested in other low latency benchmarks on the JVM, you might like
to browse our
[Embedded Key-Value Store Benchmark](https://github.com/lmdbjava/benchmarks)
and
[JVM Hashing Algorithm Benchmark](https://github.com/benalexau/hash-bench)
reports.
