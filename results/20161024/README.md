# Benchmark Results: 24 October 2016

### Introduction

This report updates a [prior report](https://github.com/benalexau/rpc-bench/blob/master/results/20160920/README.md).
Please refer to the prior report for background information.

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

#### Price Stream

The following changes applied to the price stream benchmark:

1. Aeron was configured to not perform bounds checking via its buffer

#### Machine Configuration

The following changes were made:

1. Aeron was upgraded to version 1.0.2
2. Protocol Buffers was updated to version 3.1.0
3. Linux was upgraded to version 4.7.4

### Benchmark Reproduction

This benchmark was run with Git revision 1413ae5b6412cab4cdf8d0b3a58f7c781d74e6e0.

### Results

All values are in microseconds.

#### Ping-Pong

![img](ping-pong.png)

| Percentile | [gRPC](grpc-ping-pong-1M.txt) | [Aeron](aeron-ping-pong-1M.txt) |
| ---------- | ------: | --: |
| 0.00       | 37      | 3   |
| 0.10       | 48      | 4   |
| 0.20       | 51      | 4   |
| 0.30       | 54      | 4   |
| 0.40       | 56      | 4   |
| 0.50       | 59      | 4   |
| 0.60       | 62      | 4   |
| 0.70       | 65      | 5   |
| 0.80       | 69      | 5   |
| 0.90       | 76      | 6   |
| 0.95       | 81      | 6   |
| 0.99       | 130     | 8   |
| 0.999      | 4,452   | 13  |
| 1.00       | 14,532  | 895 |

Despite ping-pong benchmark changes that would assist gRPC (eg using a single
bidirectional stream, throttling requests to a level that gRPC can easily
accommodate on this hardware based on the price stream benchmark etc), Aeron
continues to provide much lower and more stable latency.

#### Price Stream

![img](price-stream.png)

| Percentile | [gRPC](grpc-price-stream-100M.txt) | [Aeron](aeron-price-stream-100M.txt) |
| ---------- | ------------: | ----------: |
| 0.00       | 5,570         | 136         |
| 0.10       | 107,374,182   | 682,622     |
| 0.20       | 211,392,921   | 1,214,251   |
| 0.30       | 319,975,063   | 1,719,664   |
| 0.40       | 432,717,955   | 2,222,981   |
| 0.50       | 546,266,152   | 2,736,783   |
| 0.60       | 603,442,905   | 3,244,294   |
| 0.70       | 658,740,609   | 3,743,416   |
| 0.80       | 770,409,758   | 4,236,247   |
| 0.90       | 992,137,445   | 4,739,563   |
| 0.95       | 1,048,508,891 | 4,987,027   |
| 0.99       | 1,094,679,789 | 5,188,354   |
| 0.999      | 1,104,880,336 | 5,230,297   |
| 1.00       | 1,105,954,078 | 5,234,491   |

Aeron delivered over 19 million messages per second, completing the transfer
211 times faster than gRPC. This is a material improvement since the prior
benchmark report and is mostly due to buffer bounds checks being disabled.

Aeron's user-defined throughput was 44 bytes per message, consisting of an 8
byte user reserved value field (not used in the benchmark, but sent anyway), an
8 byte SBE header, and a 28 byte SBE price message. As such Aeron delivered 801
megabytes of user-defined content per second.

### Conclusion

As was seen in the prior benchmark, Aeron continues to deliver exceptional
performance and latency outcomes. The prior report's qualifier around the
very different focuses of the two stacks is again reiterated.

If you are interested in other low latency benchmarks on the JVM, you might like
to browse our
[Embedded Key-Value Store Benchmark](https://github.com/lmdbjava/benchmarks)
and
[JVM Hashing Algorithm Benchmark](https://github.com/benalexau/hash-bench)
reports.
