# Flink Netty Settings Optimization Testing Plan

**Version:** 1.0
**Date:** 2025-10-25
**Status:** Draft
**Target:** Production Cluster Optimization

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Objectives](#objectives)
3. [Scope and Prerequisites](#scope-and-prerequisites)
4. [Workload Characterization](#workload-characterization)
5. [Netty Configuration Parameters](#netty-configuration-parameters)
6. [Testing Methodology](#testing-methodology)
7. [Safety Measures and Rollback Plan](#safety-measures-and-rollback-plan)
8. [Test Scenarios](#test-scenarios)
9. [Metrics and Monitoring](#metrics-and-monitoring)
10. [Execution Plan](#execution-plan)
11. [Analysis Framework](#analysis-framework)
12. [Rollout Strategy](#rollout-strategy)
13. [Appendices](#appendices)

---

## Executive Summary

This plan outlines a systematic approach to optimize Netty network settings for Flink workloads running in production. The testing will identify optimal configurations for network throughput, latency, memory usage, and CPU utilization while maintaining stability and correctness.

**Key Goals:**
- Improve network throughput by 15-30%
- Reduce network latency (p50, p95, p99)
- Optimize memory usage for network buffers
- Minimize CPU overhead from network operations
- Maintain job stability and correctness

**Testing Approach:**
- Shadow testing with production traffic replay
- Gradual A/B testing on production subset
- Multi-dimensional optimization (throughput, latency, memory, CPU)
- Statistical validation of improvements

**Timeline:** 4-6 weeks (including analysis and rollout)

---

## Objectives

### Primary Objectives

1. **Optimize Network Throughput**
   - Measure baseline throughput (records/sec, MB/sec)
   - Identify bottlenecks in network stack
   - Test configurations to maximize throughput
   - Target: 15-30% improvement

2. **Reduce Network Latency**
   - Measure end-to-end latency distribution
   - Optimize buffer timeout settings
   - Minimize processing delays
   - Target: Reduce p99 latency by 20%+

3. **Optimize Memory Usage**
   - Right-size network buffer pools
   - Enable buffer debloating where appropriate
   - Reduce memory waste
   - Target: 10-20% memory reduction

4. **Minimize CPU Overhead**
   - Optimize thread pool sizes
   - Test transport types (NIO vs EPOLL)
   - Evaluate compression trade-offs
   - Target: Reduce CPU usage by 5-10%

### Secondary Objectives

5. **Improve Stability**
   - Reduce buffer exhaustion events
   - Optimize timeout settings
   - Improve backpressure handling

6. **Enhance Observability**
   - Enable detailed network metrics
   - Establish baseline monitoring
   - Create alerting thresholds

---

## Scope and Prerequisites

### In Scope

- All Netty-related configuration parameters
- Network buffer settings
- Thread pool configurations
- Transport layer settings
- Compression settings
- Connection management

### Out of Scope

- Application-level optimizations
- State backend tuning
- Checkpoint configuration (unless directly impacted by network)
- Cluster resource scaling
- OS-level tuning (document separately if needed)

### Prerequisites

**Required:**
- [ ] Production workload documentation (parallelism, data volumes, patterns)
- [ ] Current Flink configuration export
- [ ] Access to staging/shadow environment
- [ ] Monitoring infrastructure (Prometheus/Grafana or equivalent)
- [ ] Ability to do canary deployments
- [ ] Historical performance baselines (1-2 weeks minimum)

**Recommended:**
- [ ] Traffic replay capability
- [ ] Automated testing framework
- [ ] A/B testing infrastructure
- [ ] Distributed tracing (Jaeger/Zipkin)

**Tools:**
- Flink built-in metrics
- JMX metrics collection
- Network monitoring (iftop, nethogs, tcpdump)
- CPU/Memory profilers (async-profiler, jstat)
- Load testing tools

---

## Workload Characterization

### Step 1: Characterize Production Workload

Before testing, thoroughly document your production workload:

#### A. Job Characteristics

```yaml
Workload Profile:
  job_name: "production-job-xyz"
  execution_mode: "STREAMING" | "BATCH"
  parallelism:
    sources: X
    operators: Y
    sinks: Z
  state_size: "XXX GB"
  checkpoint_interval: "X min"

Data Characteristics:
  record_size:
    avg: "XXX bytes"
    p50: "XXX bytes"
    p95: "XXX bytes"
    p99: "XXX bytes"
  throughput:
    avg: "XXX K records/sec"
    peak: "XXX K records/sec"
  data_skew: "low" | "medium" | "high"

Network Patterns:
  shuffle_type: "rebalance" | "keyBy" | "broadcast"
  network_topology: "all-to-all" | "point-to-point" | "broadcast"
  backpressure_frequency: "rare" | "occasional" | "frequent"
```

#### B. Current Resource Usage

Collect baseline metrics:

```bash
# CPU usage per TaskManager
# Memory usage breakdown
# Network bandwidth usage (in/out)
# Network buffer usage and exhaustion events
# GC pressure
```

#### C. Performance Issues (if any)

Document existing problems:
- Backpressure events
- Network timeouts
- Buffer exhaustion warnings
- High latency periods
- Throughput degradation

### Step 2: Identify Workload Type

Classify your workload:

| Workload Type | Characteristics | Optimization Focus |
|---------------|-----------------|-------------------|
| **Throughput-Bound** | High data volumes, large records, sustained load | Buffer sizes, compression, parallelism |
| **Latency-Sensitive** | Small records, interactive queries, SLAs | Buffer timeouts, flush intervals, connection limits |
| **Memory-Constrained** | Limited resources, many jobs | Buffer debloating, memory fractions, compression |
| **CPU-Bound** | Heavy computation, transformations | Transport type, thread pools, compression trade-off |
| **Bursty Traffic** | Variable load patterns | Buffer debloating, overdraft buffers, adaptive settings |

---

## Netty Configuration Parameters

### Configuration Matrix

All tunable Netty parameters from NettyShuffleEnvironmentOptions.java and related configs:

#### Category 1: Network Buffers (Memory)

| Parameter | Default | Type | Impact | Priority |
|-----------|---------|------|--------|----------|
| `taskmanager.memory.network.min` | 64m | MemorySize | Memory allocation | **HIGH** |
| `taskmanager.memory.network.max` | Long.MAX | MemorySize | Memory allocation | **HIGH** |
| `taskmanager.memory.network.fraction` | 0.1 | Float | Memory allocation | **HIGH** |
| `taskmanager.memory.segment-size` | 32kb | MemorySize | Buffer granularity | MEDIUM |
| `taskmanager.network.memory.buffers-per-channel` | 2 | Integer | Per-channel buffers | **HIGH** |
| `taskmanager.network.memory.floating-buffers-per-gate` | 8 | Integer | Shared buffers | **HIGH** |
| `taskmanager.network.memory.max-buffers-per-channel` | 10 | Integer | Buffer limit | MEDIUM |
| `taskmanager.network.memory.max-overdraft-buffers-per-gate` | 5 | Integer | Overdraft limit | MEDIUM |
| `taskmanager.network.memory.read-buffer.required-per-gate.max` | (auto) | Integer | Input gate buffers | MEDIUM |

#### Category 2: Buffer Debloating

| Parameter | Default | Type | Impact | Priority |
|-----------|---------|------|--------|----------|
| `taskmanager.network.memory.buffer-debloat.enabled` | false | Boolean | Adaptive buffering | **HIGH** |
| `taskmanager.network.memory.buffer-debloat.period` | 200ms | Duration | Adjustment frequency | MEDIUM |
| `taskmanager.network.memory.buffer-debloat.samples` | 20 | Integer | Sample size | LOW |
| `taskmanager.network.memory.buffer-debloat.target` | 1s | Duration | Target latency | MEDIUM |
| `taskmanager.network.memory.buffer-debloat.threshold-percentages` | 25 | Integer | Change threshold | LOW |

#### Category 3: Netty Thread Pools

| Parameter | Default | Type | Impact | Priority |
|-----------|---------|------|--------|----------|
| `taskmanager.network.netty.num-arenas` | -1 (=slots) | Integer | Memory pools | MEDIUM |
| `taskmanager.network.netty.server.numThreads` | -1 (=slots) | Integer | Server threads | **HIGH** |
| `taskmanager.network.netty.client.numThreads` | -1 (=slots) | Integer | Client threads | **HIGH** |

#### Category 4: Connection Settings

| Parameter | Default | Type | Impact | Priority |
|-----------|---------|------|--------|----------|
| `taskmanager.network.max-num-tcp-connections` | 1 | Integer | Connection pooling | MEDIUM |
| `taskmanager.network.netty.server.backlog` | 0 (Netty default) | Integer | Accept queue | LOW |
| `taskmanager.network.netty.client.connectTimeoutSec` | 120 | Integer | Connection timeout | LOW |
| `taskmanager.network.retries` | 0 | Integer | Retry attempts | LOW |
| `taskmanager.network.tcp-connection.enable-reuse-across-jobs` | true | Boolean | Connection reuse | MEDIUM |

#### Category 5: Transport and Protocol

| Parameter | Default | Type | Impact | Priority |
|-----------|---------|------|--------|----------|
| `taskmanager.network.netty.transport` | "auto" | String | NIO/EPOLL | **HIGH** |
| `taskmanager.network.netty.sendReceiveBufferSize` | 0 (system) | Integer | TCP buffers | MEDIUM |
| `taskmanager.data.ssl.enabled` | true | Boolean | Encryption | LOW |

#### Category 6: Compression

| Parameter | Default | Type | Impact | Priority |
|-----------|---------|------|--------|----------|
| `taskmanager.network.batch-shuffle.compression.enabled` | true | Boolean | Compression on/off | **HIGH** |
| `taskmanager.network.compression.codec` | "LZ4" | String | Codec selection | MEDIUM |

#### Category 7: Shuffle-Specific

| Parameter | Default | Type | Impact | Priority |
|-----------|---------|------|--------|----------|
| `taskmanager.network.sort-shuffle.min-buffers` | 512 | Integer | Batch shuffle | MEDIUM |
| `taskmanager.network.sort-shuffle.min-parallelism` | 1 | Integer | Shuffle selection | LOW |
| `taskmanager.network.blocking-shuffle.type` | "file" | String | Shuffle backend | LOW |

#### Category 8: Request Backoff

| Parameter | Default | Type | Impact | Priority |
|-----------|---------|------|--------|----------|
| `taskmanager.network.request-backoff.initial` | 100ms | Integer | Initial backoff | LOW |
| `taskmanager.network.request-backoff.max` | 10000ms | Integer | Max backoff | LOW |

#### Category 9: Monitoring

| Parameter | Default | Type | Impact | Priority |
|-----------|---------|------|--------|----------|
| `taskmanager.network.detailed-metrics` | false | Boolean | Metrics detail | **HIGH** |

---

## Testing Methodology

### Overall Approach

**Multi-Phase Testing:**

1. **Phase 0: Baseline Establishment** (Week 1)
   - Collect comprehensive baseline metrics
   - Enable detailed monitoring
   - Document current configuration

2. **Phase 1: Individual Parameter Testing** (Week 1-2)
   - Test high-priority parameters individually
   - Establish parameter sensitivities
   - Identify promising configurations

3. **Phase 2: Combination Testing** (Week 2-3)
   - Test combinations of promising parameters
   - Use design of experiments (DoE) approach
   - Identify interactions and optimal sets

4. **Phase 3: Validation Testing** (Week 3-4)
   - Validate optimal configurations
   - Test under various load conditions
   - Stress testing and failure scenarios

5. **Phase 4: Production Rollout** (Week 4-6)
   - Canary deployment (5% → 25% → 50% → 100%)
   - Continuous monitoring
   - Rollback capability maintained

### Testing Environments

#### Environment 1: Shadow Environment (Recommended)

**Setup:**
- Clone production cluster configuration
- Replay production traffic
- Isolated from production impact
- Full instrumentation

**Pros:**
- Safe experimentation
- Reproducible tests
- No production impact

**Cons:**
- Infrastructure cost
- Traffic replay complexity

#### Environment 2: Staging Environment

**Setup:**
- Production-like setup
- Synthetic workload generation
- Performance testing tools

**Pros:**
- Lower cost than shadow
- Controlled testing

**Cons:**
- May not capture production patterns
- Synthetic load limitations

#### Environment 3: Production Canary

**Setup:**
- Small subset of production (5-10%)
- Real traffic
- Quick rollback capability

**Pros:**
- Real-world validation
- Actual workload patterns

**Cons:**
- Risk to production
- Requires careful monitoring

### Testing Protocol

For each configuration variant:

```yaml
Test Execution:
  1. Apply configuration
  2. Warm-up period: 10-15 minutes
  3. Measurement period: 30-60 minutes
  4. Cool-down: 5 minutes
  5. Collect metrics
  6. Revert to baseline
  7. Compare results

Test Repetitions:
  - Minimum: 3 runs per configuration
  - Calculate: mean, median, std deviation
  - Statistical significance: p < 0.05

Test Duration:
  - Short tests: 30-60 minutes
  - Long tests: 4-24 hours (stability)
  - Full validation: 1 week
```

---

## Safety Measures and Rollback Plan

### Pre-Test Safety Checks

- [ ] **Backup current configuration**
  ```bash
  kubectl get configmap flink-config -o yaml > flink-config-backup-$(date +%Y%m%d).yaml
  # OR
  cp conf/flink-conf.yaml conf/flink-conf.yaml.backup-$(date +%Y%m%d)
  ```

- [ ] **Verify rollback procedure**
  - Test configuration reload
  - Test job restart process
  - Verify no data loss on restart

- [ ] **Set up enhanced monitoring**
  - Network metrics dashboards
  - Alert thresholds defined
  - On-call rotation identified

- [ ] **Define success/failure criteria**
  - Performance improvement thresholds
  - Regression thresholds
  - Stability requirements

### Failure Detection Criteria

Automatically rollback if:

1. **Hard Failures:**
   - Job failures increase > 10%
   - TaskManager failures increase > 20%
   - Network timeout errors > 100/min
   - OutOfMemory errors
   - Checkpoint failures > 10%

2. **Soft Failures:**
   - Throughput degradation > 15%
   - Latency increase (p99) > 50%
   - CPU usage increase > 30%
   - Memory usage increase > 25%
   - Backpressure duration > 2x baseline

3. **Business Impact:**
   - SLA violations
   - Data freshness degradation
   - Downstream system impact

### Rollback Procedure

```bash
# Immediate Rollback (< 5 minutes)
# 1. Stop affected jobs
flink cancel <job-id>

# 2. Revert configuration
kubectl apply -f flink-config-backup.yaml
# OR
cp conf/flink-conf.yaml.backup conf/flink-conf.yaml

# 3. Restart jobs from last checkpoint
flink run -s <checkpoint-path> <job-jar>

# 4. Verify recovery
# - Check job status
# - Verify metrics return to baseline
# - Confirm no data loss
```

### Testing Guardrails

1. **Progressive Testing:**
   - Start with non-critical jobs
   - Start with lower traffic hours
   - Gradually increase scope

2. **Blast Radius Limitation:**
   - Test on 1 job initially
   - Expand to 5-10% of cluster
   - Then 25%, 50%, 100%

3. **Automated Monitoring:**
   - Real-time metric comparison
   - Automated alerts
   - Anomaly detection

4. **Change Management:**
   - Document each test
   - Track all configurations
   - Version control config files

---

## Test Scenarios

### Scenario Matrix

| Scenario ID | Workload Type | Configuration Focus | Priority | Duration |
|-------------|---------------|---------------------|----------|----------|
| T1 | Baseline | Current config | **CRITICAL** | 1 week |
| T2 | High Throughput | Buffer sizes, parallelism | **HIGH** | 2-3 days |
| T3 | Low Latency | Buffer timeout, flush settings | **HIGH** | 2-3 days |
| T4 | Memory Optimization | Buffer debloating, fractions | **HIGH** | 2-3 days |
| T5 | CPU Optimization | Thread pools, transport type | MEDIUM | 2 days |
| T6 | Compression Trade-off | Codecs, enable/disable | MEDIUM | 2 days |
| T7 | Bursty Traffic | Adaptive buffers, overdraft | MEDIUM | 2 days |
| T8 | High Parallelism | Connection pooling, buffers | MEDIUM | 2 days |
| T9 | Batch Workloads | Sort shuffle, compression | LOW | 1-2 days |
| T10 | Mixed Workloads | Combined optimizations | LOW | 3-4 days |

### Detailed Test Scenarios

#### T1: Baseline Establishment

**Objective:** Establish comprehensive baseline metrics

**Configuration:**
```yaml
# Current production configuration
# No changes
```

**Metrics to Collect:**
- Network throughput (records/sec, MB/sec)
- Latency distribution (p50, p95, p99, max)
- CPU usage per TaskManager
- Memory usage (heap, off-heap, network buffers)
- Network buffer statistics
- GC metrics
- Job performance metrics

**Duration:** 1 week continuous monitoring

**Success Criteria:**
- Stable baseline established
- Metrics show < 10% variance
- No anomalies or incidents

---

#### T2: High Throughput Optimization

**Objective:** Maximize network throughput for data-intensive workloads

**Test Variants:**

**T2.1: Increase Network Buffer Memory**
```yaml
Configuration:
  taskmanager.memory.network.min: 128m  # baseline: 64m
  taskmanager.memory.network.max: 2g     # baseline: 1g
  taskmanager.memory.network.fraction: 0.15  # baseline: 0.1
```

**T2.2: Increase Buffers Per Channel**
```yaml
Configuration:
  taskmanager.network.memory.buffers-per-channel: 4  # baseline: 2
  taskmanager.network.memory.floating-buffers-per-gate: 16  # baseline: 8
```

**T2.3: Increase Segment Size**
```yaml
Configuration:
  taskmanager.memory.segment-size: 64kb  # baseline: 32kb
```

**T2.4: Disable Buffer Timeout (Max Throughput)**
```yaml
Configuration:
  execution.buffer-timeout.enabled: false  # baseline: true
```

**T2.5: Enable EPOLL Transport**
```yaml
Configuration:
  taskmanager.network.netty.transport: "epoll"  # baseline: "auto"
```

**Expected Outcomes:**
- Throughput increase: 15-30%
- Possible latency increase
- Memory usage increase
- CPU usage may vary

**Metrics Focus:**
- Records/sec processed
- Network bandwidth utilization
- Buffer pool usage
- Memory allocation

---

#### T3: Low Latency Optimization

**Objective:** Minimize end-to-end latency for interactive workloads

**Test Variants:**

**T3.1: Reduce Buffer Timeout**
```yaml
Configuration:
  execution.buffer-timeout.enabled: true
  execution.buffer-timeout.interval: 10ms  # baseline: 100ms
```

**T3.2: Flush After Every Record**
```yaml
Configuration:
  execution.buffer-timeout.enabled: true
  execution.buffer-timeout.interval: 0ms  # flush immediately
```

**T3.3: Reduce Buffers Per Channel (Lower Buffering)**
```yaml
Configuration:
  taskmanager.network.memory.buffers-per-channel: 1  # baseline: 2
  taskmanager.network.memory.max-buffers-per-channel: 5  # baseline: 10
```

**T3.4: Increase Server Threads (Lower Queueing)**
```yaml
Configuration:
  taskmanager.network.netty.server.numThreads: 16  # baseline: slots (e.g., 4)
  taskmanager.network.netty.client.numThreads: 16  # baseline: slots
```

**T3.5: Reduce Backlog**
```yaml
Configuration:
  taskmanager.network.netty.server.backlog: 50  # baseline: 0 (Netty default ~128)
```

**Expected Outcomes:**
- Latency reduction: 20-40%
- Possible throughput decrease
- CPU usage may increase (more frequent flushing)

**Metrics Focus:**
- p50, p95, p99 latency
- Buffer flush frequency
- CPU usage
- Throughput impact

---

#### T4: Memory Optimization (Buffer Debloating)

**Objective:** Minimize memory usage while maintaining performance

**Test Variants:**

**T4.1: Enable Buffer Debloating (Conservative)**
```yaml
Configuration:
  taskmanager.network.memory.buffer-debloat.enabled: true
  taskmanager.network.memory.buffer-debloat.target: 1s  # baseline target
  taskmanager.network.memory.buffer-debloat.period: 200ms
```

**T4.2: Aggressive Debloating**
```yaml
Configuration:
  taskmanager.network.memory.buffer-debloat.enabled: true
  taskmanager.network.memory.buffer-debloat.target: 500ms  # more aggressive
  taskmanager.network.memory.buffer-debloat.period: 100ms  # faster reaction
```

**T4.3: Reduce Network Memory Fraction**
```yaml
Configuration:
  taskmanager.memory.network.fraction: 0.075  # baseline: 0.1
  taskmanager.memory.network.min: 64m
  # Enable debloating to compensate
  taskmanager.network.memory.buffer-debloat.enabled: true
```

**T4.4: Smaller Segment Size**
```yaml
Configuration:
  taskmanager.memory.segment-size: 16kb  # baseline: 32kb
```

**Expected Outcomes:**
- Memory usage reduction: 10-25%
- Potential latency improvement (less buffering)
- Throughput may vary
- More adaptive to load changes

**Metrics Focus:**
- Network memory usage
- Buffer pool statistics
- Performance stability under varying load
- Backpressure handling

---

#### T5: CPU Optimization

**Objective:** Reduce CPU overhead from network operations

**Test Variants:**

**T5.1: EPOLL Transport (Linux-specific)**
```yaml
Configuration:
  taskmanager.network.netty.transport: "epoll"  # baseline: "auto"
```

**T5.2: Optimize Thread Pool Sizes**
```yaml
Configuration:
  # For low parallelism jobs
  taskmanager.network.netty.server.numThreads: 4
  taskmanager.network.netty.client.numThreads: 4
  taskmanager.network.netty.num-arenas: 4
```

**T5.3: Disable Compression (CPU-bound workloads)**
```yaml
Configuration:
  taskmanager.network.batch-shuffle.compression.enabled: false  # baseline: true
```

**T5.4: Connection Pooling**
```yaml
Configuration:
  taskmanager.network.max-num-tcp-connections: 2  # baseline: 1
  taskmanager.network.tcp-connection.enable-reuse-across-jobs: true
```

**Expected Outcomes:**
- CPU usage reduction: 5-15%
- Throughput may increase (less CPU contention)
- Memory usage may vary

**Metrics Focus:**
- CPU utilization per TaskManager
- CPU time in network operations
- GC pressure
- Throughput

---

#### T6: Compression Trade-off Analysis

**Objective:** Find optimal compression settings for your workload

**Test Variants:**

**T6.1: No Compression**
```yaml
Configuration:
  taskmanager.network.batch-shuffle.compression.enabled: false
```

**T6.2: LZ4 (Default - Fast)**
```yaml
Configuration:
  taskmanager.network.batch-shuffle.compression.enabled: true
  taskmanager.network.compression.codec: "LZ4"
```

**T6.3: ZSTD (High Compression)**
```yaml
Configuration:
  taskmanager.network.batch-shuffle.compression.enabled: true
  taskmanager.network.compression.codec: "ZSTD"
```

**T6.4: LZO (Balanced)**
```yaml
Configuration:
  taskmanager.network.batch-shuffle.compression.enabled: true
  taskmanager.network.compression.codec: "LZO"
```

**Analysis Criteria:**
- Compression ratio achieved
- CPU overhead
- Throughput impact
- Network bandwidth savings
- I/O impact (for batch workloads)

**Metrics Focus:**
- Compressed vs uncompressed data size
- CPU usage
- Network bandwidth
- Disk I/O (batch jobs)
- Throughput

---

#### T7: Bursty Traffic Handling

**Objective:** Optimize for variable load patterns

**Test Variants:**

**T7.1: Buffer Debloating + Overdraft**
```yaml
Configuration:
  taskmanager.network.memory.buffer-debloat.enabled: true
  taskmanager.network.memory.buffer-debloat.target: 1s
  taskmanager.network.memory.max-overdraft-buffers-per-gate: 10  # baseline: 5
```

**T7.2: Larger Floating Buffer Pool**
```yaml
Configuration:
  taskmanager.network.memory.floating-buffers-per-gate: 16  # baseline: 8
  taskmanager.network.memory.max-buffers-per-channel: 15  # baseline: 10
```

**T7.3: Adaptive Buffer Timeout**
```yaml
Configuration:
  execution.buffer-timeout.enabled: true
  execution.buffer-timeout.interval: 100ms
  taskmanager.network.memory.buffer-debloat.enabled: true
```

**Test Methodology:**
- Inject variable load patterns
- Measure response to traffic spikes
- Check backpressure handling
- Verify no buffer exhaustion

**Metrics Focus:**
- Buffer utilization over time
- Backpressure events
- Performance variance
- Buffer exhaustion warnings

---

#### T8: High Parallelism Optimization

**Objective:** Optimize for jobs with very high parallelism (1000+ tasks)

**Test Variants:**

**T8.1: Reduced Per-Channel Buffers**
```yaml
Configuration:
  taskmanager.network.memory.buffers-per-channel: 1  # baseline: 2
  taskmanager.network.memory.floating-buffers-per-gate: 32  # baseline: 8
  taskmanager.memory.network.fraction: 0.15  # baseline: 0.1
```

**T8.2: Connection Pooling**
```yaml
Configuration:
  taskmanager.network.max-num-tcp-connections: 3  # baseline: 1
  taskmanager.network.tcp-connection.enable-reuse-across-jobs: true
```

**T8.3: Limit Required Buffers**
```yaml
Configuration:
  taskmanager.network.memory.read-buffer.required-per-gate.max: 1000
```

**Metrics Focus:**
- Total network buffer usage
- Connection count
- Buffer allocation failures
- Scalability limits

---

#### T9: Batch Workload Optimization

**Objective:** Optimize for batch processing jobs

**Test Variants:**

**T9.1: Sort Shuffle Optimization**
```yaml
Configuration:
  taskmanager.network.sort-shuffle.min-buffers: 2048  # baseline: 512
  taskmanager.memory.framework.off-heap.batch-shuffle.size: 128m  # baseline: 64m
```

**T9.2: Compression for Batch**
```yaml
Configuration:
  taskmanager.network.batch-shuffle.compression.enabled: true
  taskmanager.network.compression.codec: "ZSTD"  # Best compression ratio
```

**T9.3: Disable Buffer Timeout (Batch)**
```yaml
Configuration:
  execution.buffer-timeout.enabled: false
  execution.runtime-mode: BATCH
```

**Metrics Focus:**
- Job completion time
- Disk I/O
- Network bandwidth
- Compression ratio
- Spill statistics

---

#### T10: Combined Optimization (Final Validation)

**Objective:** Test optimal combination of all parameters

**Approach:**
1. Select best configurations from T2-T9
2. Test combinations
3. Validate no negative interactions
4. Long-term stability testing (1 week)

**Example Combined Configuration:**
```yaml
# Network Memory
taskmanager.memory.network.fraction: 0.12
taskmanager.memory.network.min: 128m

# Buffer Settings
taskmanager.network.memory.buffers-per-channel: 3
taskmanager.network.memory.floating-buffers-per-gate: 12

# Buffer Debloating
taskmanager.network.memory.buffer-debloat.enabled: true
taskmanager.network.memory.buffer-debloat.target: 800ms

# Thread Pools
taskmanager.network.netty.server.numThreads: 8
taskmanager.network.netty.client.numThreads: 8

# Transport
taskmanager.network.netty.transport: "epoll"

# Compression (if applicable)
taskmanager.network.batch-shuffle.compression.enabled: true
taskmanager.network.compression.codec: "LZ4"

# Monitoring
taskmanager.network.detailed-metrics: true
```

---

## Metrics and Monitoring

### Core Metrics to Collect

#### 1. Network Throughput Metrics

```
Metrics:
  - flink.taskmanager.network.inputRate (records/sec)
  - flink.taskmanager.network.outputRate (records/sec)
  - flink.taskmanager.network.inputBytes (bytes/sec)
  - flink.taskmanager.network.outputBytes (bytes/sec)

Collection:
  - Granularity: Per TaskManager, per gate
  - Frequency: 10-second intervals
  - Aggregation: avg, p50, p95, p99, max
```

#### 2. Network Buffer Metrics

```
Metrics:
  - flink.taskmanager.network.bufferPoolUsage
  - flink.taskmanager.network.bufferPoolSize
  - flink.taskmanager.network.availableMemorySegments
  - flink.taskmanager.network.requestedMemorySegments
  - flink.taskmanager.network.usedMemorySegments

Per-Channel:
  - inputBufferUsage
  - outputBufferUsage
  - inPoolUsage
  - outPoolUsage
```

#### 3. Latency Metrics

```
Metrics:
  - flink.operator.latency (end-to-end)
  - flink.taskmanager.network.latency (network only)

Collection:
  - Latency markers: Enable with latency tracking interval
  - Distribution: p50, p95, p99, max
  - Per-operator latency breakdown
```

#### 4. CPU and Memory Metrics

```
System Metrics:
  - CPU usage per TaskManager
  - CPU usage per thread (network threads)
  - JVM heap usage
  - JVM off-heap usage (network buffers)
  - GC pressure (collection frequency, pause time)

Network-Specific:
  - CPU time in Netty threads
  - CPU time in serialization/deserialization
  - Memory allocation rate
```

#### 5. Backpressure and Flow Control

```
Metrics:
  - flink.taskmanager.task.backPressuredTimeMsPerSecond
  - flink.taskmanager.task.busyTimeMsPerSecond
  - flink.taskmanager.task.idleTimeMsPerSecond

Additional:
  - Credit-based flow control metrics
  - Buffer bloat metrics
  - Overdraft buffer usage
```

#### 6. Error and Retry Metrics

```
Metrics:
  - Network connection failures
  - Network timeout errors
  - Buffer exhaustion events
  - Retry counts
  - Partition request failures
```

#### 7. Job-Level Metrics

```
Metrics:
  - Job throughput (overall)
  - Checkpoint duration
  - Checkpoint alignment time
  - Job lag (for streaming)
  - Task failure rate
```

### Monitoring Setup

#### Step 1: Enable Detailed Metrics

```yaml
# flink-conf.yaml
metrics.reporter.prom.class: org.apache.flink.metrics.prometheus.PrometheusReporter
metrics.reporter.prom.port: 9249

# Enable detailed network metrics
taskmanager.network.detailed-metrics: true

# Enable latency tracking
metrics.latency.interval: 1000
metrics.latency.granularity: operator
```

#### Step 2: Prometheus Configuration

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'flink-taskmanagers'
    scrape_interval: 10s
    static_configs:
      - targets: ['taskmanager-1:9249', 'taskmanager-2:9249', ...]
    relabel_configs:
      - source_labels: [__address__]
        target_label: instance
```

#### Step 3: Grafana Dashboards

Create dashboards for:

1. **Network Overview Dashboard**
   - Throughput timeseries
   - Latency distribution
   - Buffer utilization
   - Error rates

2. **Buffer Analysis Dashboard**
   - Buffer pool usage per TaskManager
   - Per-channel buffer usage
   - Buffer debloat metrics
   - Floating buffer usage

3. **Performance Comparison Dashboard**
   - Side-by-side comparison of test variants
   - Statistical significance indicators
   - Regression detection

4. **System Resource Dashboard**
   - CPU usage
   - Memory usage
   - GC metrics
   - Network bandwidth

#### Step 4: Alerting Rules

```yaml
# Prometheus alerts
groups:
  - name: flink_network_alerts
    rules:
      - alert: NetworkBufferExhaustion
        expr: flink_taskmanager_network_availableMemorySegments < 100
        for: 1m
        annotations:
          summary: "Network buffers running low"

      - alert: HighNetworkLatency
        expr: flink_operator_latency{quantile="0.99"} > 5000
        for: 5m
        annotations:
          summary: "Network latency p99 exceeds 5 seconds"

      - alert: NetworkConnectionFailures
        expr: rate(flink_taskmanager_network_connection_failures[5m]) > 1
        for: 2m
        annotations:
          summary: "Network connection failures detected"
```

### Metric Collection Tools

#### Option 1: Flink Web UI

- Navigate to: `http://jobmanager:8081/#/task-manager/{taskmanager-id}/metrics`
- View real-time metrics
- Limited historical data

#### Option 2: REST API Polling

```bash
#!/bin/bash
# collect-metrics.sh

JOBMANAGER_URL="http://localhost:8081"
TASKMANAGER_ID="<taskmanager-id>"

curl "${JOBMANAGER_URL}/taskmanagers/${TASKMANAGER_ID}/metrics" | jq .
```

#### Option 3: JMX Monitoring

```yaml
# Enable JMX
env.java.opts: "-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9010 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
```

```bash
# Query JMX
jmxterm -l localhost:9010 -n
> domains
> beans
> get -b org.apache.flink:key=*,name=* *
```

#### Option 4: Custom Metric Reporter

Implement custom reporter for specialized metrics:

```java
public class CustomNetworkMetricReporter implements MetricReporter {
    @Override
    public void notifyOfAddedMetric(Metric metric, String metricName, MetricGroup group) {
        // Custom handling
    }
}
```

---

## Execution Plan

### Week-by-Week Breakdown

#### Week 1: Preparation and Baseline

**Day 1-2: Environment Setup**
- [ ] Set up shadow/staging environment
- [ ] Configure monitoring and dashboards
- [ ] Enable detailed metrics
- [ ] Set up alerting
- [ ] Document current configuration

**Day 3-7: Baseline Collection**
- [ ] Run baseline tests (T1)
- [ ] Collect comprehensive metrics
- [ ] Analyze workload patterns
- [ ] Identify performance bottlenecks
- [ ] Document baseline statistics

**Deliverables:**
- Baseline metrics report
- Workload characterization document
- Testing environment ready
- Monitoring dashboards configured

---

#### Week 2: Individual Parameter Testing

**Day 1-2: High-Priority Tests**
- [ ] T2: Throughput optimization tests
- [ ] T3: Latency optimization tests
- [ ] Collect and analyze results

**Day 3-4: Medium-Priority Tests**
- [ ] T4: Memory optimization (debloating)
- [ ] T5: CPU optimization
- [ ] Collect and analyze results

**Day 5: Analysis**
- [ ] Compare results to baseline
- [ ] Identify best individual configurations
- [ ] Document findings
- [ ] Plan combination tests

**Deliverables:**
- Individual parameter test results
- Performance impact analysis
- Recommendations for combinations

---

#### Week 3: Combination Testing and Validation

**Day 1-3: Combination Tests**
- [ ] Test promising parameter combinations
- [ ] T10: Combined optimization scenarios
- [ ] Test for negative interactions
- [ ] Refine configurations

**Day 4-5: Stress and Stability Testing**
- [ ] Long-running tests (24+ hours)
- [ ] Variable load testing
- [ ] Failure scenario testing
- [ ] Resource exhaustion testing

**Deliverables:**
- Optimal configuration(s) identified
- Stability test results
- Risk assessment

---

#### Week 4: Production Validation (Canary)

**Day 1: Canary Preparation**
- [ ] Select canary job(s)
- [ ] Prepare rollback plan
- [ ] Set up enhanced monitoring
- [ ] Communication plan

**Day 2-3: 5% Canary**
- [ ] Deploy to 1 non-critical job
- [ ] Monitor for 48 hours
- [ ] Collect metrics
- [ ] Analyze for regressions

**Day 4-5: 25% Canary**
- [ ] Expand to multiple jobs
- [ ] Monitor for 48 hours
- [ ] Collect metrics
- [ ] Validate improvements

**Deliverables:**
- Canary test results
- Go/no-go decision for wider rollout

---

#### Week 5-6: Progressive Rollout

**Week 5:**
- Day 1-2: 50% rollout
- Day 3-5: Monitor and validate
- Weekend: Observe under weekend load patterns

**Week 6:**
- Day 1-2: 100% rollout
- Day 3-5: Final monitoring and validation
- End of week: Document final results

**Deliverables:**
- Production rollout complete
- Performance improvement documented
- Updated configuration standards
- Lessons learned document

---

## Analysis Framework

### Statistical Analysis

#### 1. Baseline Comparison

For each metric, compare test configuration to baseline:

```python
# Example Python analysis
from scipy import stats

def compare_to_baseline(baseline_metrics, test_metrics):
    """
    Compare test metrics to baseline with statistical significance
    """
    # Calculate mean, median, std dev
    baseline_mean = np.mean(baseline_metrics)
    test_mean = np.mean(test_metrics)

    # Percent change
    pct_change = ((test_mean - baseline_mean) / baseline_mean) * 100

    # Statistical significance (t-test)
    t_stat, p_value = stats.ttest_ind(baseline_metrics, test_metrics)
    significant = p_value < 0.05

    return {
        'baseline_mean': baseline_mean,
        'test_mean': test_mean,
        'pct_change': pct_change,
        'p_value': p_value,
        'significant': significant
    }
```

#### 2. Multi-Objective Scoring

Create composite score for overall performance:

```python
def calculate_composite_score(metrics):
    """
    Composite score considering multiple objectives
    Weights can be adjusted based on priorities
    """
    weights = {
        'throughput': 0.30,    # 30%
        'latency': 0.30,       # 30%
        'memory': 0.20,        # 20%
        'cpu': 0.15,           # 15%
        'stability': 0.05      # 5%
    }

    # Normalize metrics to [0, 1] scale
    normalized = normalize_metrics(metrics)

    # Calculate weighted sum
    score = sum(normalized[k] * weights[k] for k in weights)

    return score
```

#### 3. Regression Detection

```python
def detect_regressions(baseline, test, thresholds):
    """
    Detect performance regressions
    """
    regressions = []

    checks = [
        ('throughput', -15, 'decrease'),  # 15% decrease is regression
        ('latency_p99', 50, 'increase'),  # 50% increase is regression
        ('cpu_usage', 30, 'increase'),    # 30% increase is regression
        ('error_rate', 10, 'increase'),   # 10% increase is regression
    ]

    for metric, threshold, direction in checks:
        pct_change = calculate_pct_change(baseline[metric], test[metric])

        if direction == 'increase' and pct_change > threshold:
            regressions.append(f"{metric} increased by {pct_change}%")
        elif direction == 'decrease' and pct_change < -threshold:
            regressions.append(f"{metric} decreased by {pct_change}%")

    return regressions
```

### Test Result Template

```markdown
# Test Result: T2.1 - Increased Network Buffer Memory

## Configuration
- taskmanager.memory.network.fraction: 0.15 (baseline: 0.1)

## Test Details
- Duration: 60 minutes
- Environment: Shadow cluster
- Workload: Production replay
- Test runs: 3

## Results

### Throughput
- Baseline: 125,000 records/sec (σ = 3,200)
- Test: 152,000 records/sec (σ = 2,800)
- Change: +21.6% (p < 0.001) ✓ **Significant improvement**

### Latency (p99)
- Baseline: 245 ms (σ = 15)
- Test: 268 ms (σ = 18)
- Change: +9.4% (p = 0.032) ⚠️ **Slight regression**

### Memory Usage
- Baseline: 2.8 GB network buffers
- Test: 4.1 GB network buffers
- Change: +46.4% **As expected**

### CPU Usage
- Baseline: 58% avg
- Test: 56% avg
- Change: -3.4% (p = 0.124) **No significant change**

### Backpressure
- Baseline: 12% of time
- Test: 5% of time
- Change: -58.3% ✓ **Significant improvement**

## Analysis
- **Pros**: Significant throughput improvement, reduced backpressure
- **Cons**: Slight latency increase, higher memory usage
- **Recommendation**: Good for throughput-bound workloads with sufficient memory

## Next Steps
- Combine with buffer debloating (T4) to reduce memory usage
- Test under peak load conditions
```

### Decision Matrix

| Configuration | Throughput | Latency | Memory | CPU | Stability | Score | Recommendation |
|---------------|------------|---------|--------|-----|-----------|-------|----------------|
| Baseline | 0% | 0% | 0% | 0% | 5/5 | - | Reference |
| T2.1 | +21% | -9% | +46% | 0% | 5/5 | 7.2 | Throughput workloads |
| T3.1 | -5% | +35% | 0% | +8% | 5/5 | 7.8 | Latency workloads |
| T4.1 | +3% | +5% | -18% | 0% | 5/5 | 7.5 | Memory-constrained |
| T10.Combined | +18% | +15% | +12% | -5% | 5/5 | 8.9 | **RECOMMENDED** |

---

## Rollout Strategy

### Phase 0: Pre-Rollout Checklist

- [ ] All tests completed successfully
- [ ] Optimal configuration(s) identified
- [ ] Statistical significance validated
- [ ] No critical regressions detected
- [ ] Rollback plan tested and ready
- [ ] Monitoring dashboards updated
- [ ] Alert thresholds adjusted
- [ ] Change management approval
- [ ] Communication plan executed
- [ ] On-call team briefed

### Phase 1: Canary Deployment (Week 4)

**Stage 1A: Single Job (Day 1-2)**
```yaml
Scope:
  - Jobs: 1 non-critical job
  - Traffic: ~1% of cluster
  - Duration: 48 hours

Success Criteria:
  - No job failures
  - Performance improvement confirmed
  - No alerts triggered
  - Metrics stable

Rollback Trigger:
  - Any job failure
  - Performance regression > 10%
  - Any critical alert
```

**Stage 1B: Multiple Jobs (Day 3-5)**
```yaml
Scope:
  - Jobs: 3-5 jobs of varying types
  - Traffic: ~5% of cluster
  - Duration: 48-72 hours

Success Criteria:
  - All jobs stable
  - Performance improvements across job types
  - Resource usage as expected
  - No negative feedback

Rollback Trigger:
  - >1 job failure
  - Consistent performance regression
  - Resource exhaustion
```

### Phase 2: Progressive Rollout (Week 5-6)

**Stage 2A: 25% Rollout**
```yaml
Scope:
  - Jobs: ~25% of cluster jobs
  - Include mix of job types
  - Duration: 3-4 days

Monitoring:
  - Hourly metric checks (first 24h)
  - 4-hourly checks (after 24h)
  - Daily performance reports

Success Criteria:
  - Aggregate performance improvement
  - No increase in incidents
  - Resource usage within limits
```

**Stage 2B: 50% Rollout**
```yaml
Scope:
  - Jobs: ~50% of cluster jobs
  - Include production-critical jobs
  - Duration: 3-4 days

Monitoring:
  - Continuous automated monitoring
  - Daily performance reports
  - Weekly review with stakeholders

Success Criteria:
  - Sustained performance improvement
  - No production incidents
  - Positive business impact
```

**Stage 2C: 100% Rollout**
```yaml
Scope:
  - Jobs: All remaining jobs
  - Duration: Ongoing

Monitoring:
  - Standard production monitoring
  - Weekly performance reviews (first month)
  - Monthly reviews (ongoing)

Success Criteria:
  - Cluster-wide performance improvement
  - Documented ROI
  - No rollback required
```

### Rollout Automation

```bash
#!/bin/bash
# rollout.sh - Automated canary deployment

CONFIGMAP="flink-config"
NAMESPACE="flink"
CANARY_JOBS=("job-1" "job-2" "job-3")

function deploy_config() {
    local config_file=$1
    kubectl apply -f $config_file -n $NAMESPACE
}

function restart_job() {
    local job_name=$1
    # Stop job
    flink cancel $job_name
    # Deploy with new config
    flink run -d -s latest-checkpoint $job_name.jar
}

function check_health() {
    local job_id=$1
    local status=$(flink list | grep $job_id | awk '{print $2}')
    if [ "$status" != "RUNNING" ]; then
        echo "ERROR: Job $job_id not running"
        return 1
    fi
    return 0
}

function rollback() {
    echo "Initiating rollback..."
    kubectl apply -f flink-config-backup.yaml -n $NAMESPACE
    # Restart jobs
    for job in "${CANARY_JOBS[@]}"; do
        restart_job $job
    done
}

# Main rollout logic
echo "Starting canary rollout..."
deploy_config "flink-config-optimized.yaml"

for job in "${CANARY_JOBS[@]}"; do
    echo "Deploying to $job..."
    restart_job $job

    # Wait and health check
    sleep 60
    if ! check_health $job; then
        rollback
        exit 1
    fi
done

echo "Canary rollout successful"
```

### Monitoring During Rollout

**Real-time Dashboard:**
```
+---------------------------+---------------------------+
|   Throughput Comparison   |    Latency Comparison     |
|  Baseline vs Optimized    |   Baseline vs Optimized   |
+---------------------------+---------------------------+
|    Memory Usage Delta     |     CPU Usage Delta       |
|                           |                           |
+---------------------------+---------------------------+
|    Active Alerts          |    Job Health Status      |
|                           |                           |
+---------------------------+---------------------------+
```

**Automated Checks:**
- Every 5 minutes: Health check
- Every 15 minutes: Performance comparison
- Every hour: Regression analysis
- Every 4 hours: Comprehensive report

---

## Appendices

### Appendix A: Configuration Templates

#### A.1: High Throughput Configuration

```yaml
# flink-conf-high-throughput.yaml

# Network Memory
taskmanager.memory.network.fraction: 0.15
taskmanager.memory.network.min: 128m
taskmanager.memory.network.max: 4g

# Buffer Settings
taskmanager.memory.segment-size: 64kb
taskmanager.network.memory.buffers-per-channel: 4
taskmanager.network.memory.floating-buffers-per-gate: 16
taskmanager.network.memory.max-buffers-per-channel: 20

# Buffer Timeout (prioritize throughput)
execution.buffer-timeout.enabled: false

# Netty Settings
taskmanager.network.netty.transport: "epoll"
taskmanager.network.netty.server.numThreads: 8
taskmanager.network.netty.client.numThreads: 8
taskmanager.network.netty.num-arenas: 8

# Connection Settings
taskmanager.network.max-num-tcp-connections: 2
taskmanager.network.tcp-connection.enable-reuse-across-jobs: true

# Monitoring
taskmanager.network.detailed-metrics: true
```

#### A.2: Low Latency Configuration

```yaml
# flink-conf-low-latency.yaml

# Network Memory
taskmanager.memory.network.fraction: 0.1
taskmanager.memory.network.min: 64m

# Buffer Settings
taskmanager.memory.segment-size: 32kb
taskmanager.network.memory.buffers-per-channel: 1
taskmanager.network.memory.floating-buffers-per-gate: 8
taskmanager.network.memory.max-buffers-per-channel: 5

# Buffer Timeout (prioritize latency)
execution.buffer-timeout.enabled: true
execution.buffer-timeout.interval: 10ms

# Netty Settings
taskmanager.network.netty.transport: "epoll"
taskmanager.network.netty.server.numThreads: 16
taskmanager.network.netty.client.numThreads: 16

# Monitoring
taskmanager.network.detailed-metrics: true
```

#### A.3: Memory Optimized Configuration

```yaml
# flink-conf-memory-optimized.yaml

# Network Memory (reduced)
taskmanager.memory.network.fraction: 0.075
taskmanager.memory.network.min: 64m

# Buffer Settings
taskmanager.memory.segment-size: 32kb
taskmanager.network.memory.buffers-per-channel: 2
taskmanager.network.memory.floating-buffers-per-gate: 8

# Buffer Debloating (enabled)
taskmanager.network.memory.buffer-debloat.enabled: true
taskmanager.network.memory.buffer-debloat.target: 1s
taskmanager.network.memory.buffer-debloat.period: 200ms
taskmanager.network.memory.buffer-debloat.threshold-percentages: 25

# Standard buffer timeout
execution.buffer-timeout.enabled: true
execution.buffer-timeout.interval: 100ms

# Netty Settings
taskmanager.network.netty.transport: "auto"

# Monitoring
taskmanager.network.detailed-metrics: true
```

#### A.4: Balanced Configuration (Recommended Starting Point)

```yaml
# flink-conf-balanced.yaml

# Network Memory
taskmanager.memory.network.fraction: 0.12
taskmanager.memory.network.min: 96m
taskmanager.memory.network.max: 2g

# Buffer Settings
taskmanager.memory.segment-size: 32kb
taskmanager.network.memory.buffers-per-channel: 3
taskmanager.network.memory.floating-buffers-per-gate: 12
taskmanager.network.memory.max-buffers-per-channel: 12

# Buffer Debloating
taskmanager.network.memory.buffer-debloat.enabled: true
taskmanager.network.memory.buffer-debloat.target: 800ms
taskmanager.network.memory.buffer-debloat.period: 200ms

# Buffer Timeout
execution.buffer-timeout.enabled: true
execution.buffer-timeout.interval: 100ms

# Netty Settings
taskmanager.network.netty.transport: "epoll"
taskmanager.network.netty.server.numThreads: 8
taskmanager.network.netty.client.numThreads: 8
taskmanager.network.netty.num-arenas: 8

# Connection Settings
taskmanager.network.max-num-tcp-connections: 1
taskmanager.network.tcp-connection.enable-reuse-across-jobs: true

# Monitoring
taskmanager.network.detailed-metrics: true
```

### Appendix B: Metric Collection Scripts

#### B.1: Prometheus Query Examples

```promql
# Network throughput
rate(flink_taskmanager_network_inputBytes[5m])
rate(flink_taskmanager_network_outputBytes[5m])

# Buffer usage
flink_taskmanager_network_usedMemorySegments / flink_taskmanager_network_totalMemorySegments

# Latency percentiles
histogram_quantile(0.99, flink_operator_latency_bucket)

# Backpressure
rate(flink_taskmanager_task_backPressuredTimeMsPerSecond[5m])

# CPU usage (from node exporter)
rate(process_cpu_seconds_total{job="flink-taskmanager"}[5m]) * 100
```

#### B.2: Automated Test Execution Script

```bash
#!/bin/bash
# run-netty-tests.sh

TEST_DURATION=3600  # 1 hour
WARMUP_DURATION=600  # 10 minutes
COOLDOWN_DURATION=300  # 5 minutes

BASELINE_CONFIG="flink-conf-baseline.yaml"
TEST_CONFIGS=(
    "flink-conf-t2.1.yaml"
    "flink-conf-t2.2.yaml"
    "flink-conf-t3.1.yaml"
    # ... more configs
)

METRICS_URL="http://prometheus:9090/api/v1/query_range"
RESULTS_DIR="test-results"

mkdir -p $RESULTS_DIR

function apply_config() {
    local config=$1
    kubectl apply -f $config -n flink
    kubectl rollout restart deployment/flink-taskmanager -n flink
    kubectl rollout status deployment/flink-taskmanager -n flink
}

function collect_metrics() {
    local test_name=$1
    local start_time=$2
    local end_time=$3

    # Collect metrics from Prometheus
    curl -G "$METRICS_URL" \
        --data-urlencode "query=rate(flink_taskmanager_network_inputBytes[5m])" \
        --data-urlencode "start=$start_time" \
        --data-urlencode "end=$end_time" \
        --data-urlencode "step=10s" \
        > "$RESULTS_DIR/${test_name}_throughput.json"

    # Collect more metrics...
}

function run_test() {
    local config=$1
    local test_name=$(basename $config .yaml)

    echo "Running test: $test_name"

    # Apply configuration
    apply_config $config

    # Warmup period
    echo "Warmup period: ${WARMUP_DURATION}s"
    sleep $WARMUP_DURATION

    # Test period
    echo "Test period: ${TEST_DURATION}s"
    start_time=$(date +%s)
    sleep $TEST_DURATION
    end_time=$(date +%s)

    # Collect metrics
    echo "Collecting metrics..."
    collect_metrics $test_name $start_time $end_time

    # Cooldown period
    echo "Cooldown period: ${COOLDOWN_DURATION}s"
    sleep $COOLDOWN_DURATION

    echo "Test $test_name completed"
}

# Run baseline
echo "Running baseline test..."
run_test $BASELINE_CONFIG

# Run all test configurations
for config in "${TEST_CONFIGS[@]}"; do
    run_test $config
done

echo "All tests completed. Results in $RESULTS_DIR"
```

#### B.3: Results Analysis Script

```python
#!/usr/bin/env python3
# analyze-results.py

import json
import numpy as np
from scipy import stats
import matplotlib.pyplot as plt
import pandas as pd

def load_metrics(test_name):
    """Load metrics from JSON files"""
    with open(f'test-results/{test_name}_throughput.json') as f:
        throughput = json.load(f)
    # Load other metrics...
    return throughput

def calculate_statistics(data):
    """Calculate statistical measures"""
    return {
        'mean': np.mean(data),
        'median': np.median(data),
        'std': np.std(data),
        'p95': np.percentile(data, 95),
        'p99': np.percentile(data, 99),
    }

def compare_to_baseline(baseline, test):
    """Statistical comparison"""
    t_stat, p_value = stats.ttest_ind(baseline, test)

    baseline_mean = np.mean(baseline)
    test_mean = np.mean(test)
    pct_change = ((test_mean - baseline_mean) / baseline_mean) * 100

    return {
        'pct_change': pct_change,
        'p_value': p_value,
        'significant': p_value < 0.05
    }

def generate_report(test_results):
    """Generate comprehensive report"""
    report = []

    for test_name, results in test_results.items():
        report.append(f"\n## Test: {test_name}")
        report.append(f"Throughput change: {results['throughput_pct']:.2f}%")
        report.append(f"Latency change: {results['latency_pct']:.2f}%")
        report.append(f"Significant: {results['significant']}")

    return "\n".join(report)

# Main execution
if __name__ == "__main__":
    baseline = load_metrics("baseline")

    test_results = {}
    for test in ["t2.1", "t2.2", "t3.1", ...]:
        test_data = load_metrics(test)
        comparison = compare_to_baseline(baseline, test_data)
        test_results[test] = comparison

    report = generate_report(test_results)
    print(report)

    # Save report
    with open('test-results/analysis-report.md', 'w') as f:
        f.write(report)
```

### Appendix C: Troubleshooting Guide

#### Common Issues and Solutions

**Issue 1: Network Buffer Exhaustion**
```
Symptoms:
- Warning: "Insufficient number of network buffers"
- High backpressure
- Reduced throughput

Root Causes:
- Network memory too small
- Too many concurrent tasks
- Large parallelism

Solutions:
1. Increase taskmanager.memory.network.fraction
2. Increase taskmanager.memory.network.min
3. Enable buffer debloating
4. Reduce parallelism
```

**Issue 2: High Network Latency**
```
Symptoms:
- Increased end-to-end latency
- Slow checkpoint alignment
- User-facing latency SLA violations

Root Causes:
- Buffer timeout too high
- Too much buffering
- Network congestion

Solutions:
1. Reduce execution.buffer-timeout.interval
2. Reduce buffers-per-channel
3. Check network bandwidth
4. Enable detailed metrics for analysis
```

**Issue 3: High CPU Usage**
```
Symptoms:
- High CPU usage in Netty threads
- GC pressure
- Reduced throughput

Root Causes:
- Compression overhead
- Too many threads
- Serialization bottleneck

Solutions:
1. Disable compression for CPU-bound workloads
2. Optimize thread pool sizes
3. Switch to EPOLL transport (Linux)
4. Profile to identify hotspots
```

**Issue 4: Connection Timeouts**
```
Symptoms:
- PartitionConnectionException
- Connection timeout errors
- Job failures

Root Causes:
- Network issues
- Timeout too aggressive
- Firewall/routing problems

Solutions:
1. Increase client.connectTimeoutSec
2. Enable network.retries
3. Check network infrastructure
4. Verify firewall rules
```

### Appendix D: References

#### Flink Documentation
- [Network Memory Tuning Guide](https://nightlies.apache.org/flink/flink-docs-master/docs/deployment/memory/network_mem_tuning/)
- [Configuration Reference](https://nightlies.apache.org/flink/flink-docs-master/docs/deployment/config/)
- [Monitoring & Metrics](https://nightlies.apache.org/flink/flink-docs-master/docs/ops/metrics/)

#### Related Work
- Flink Network Stack Architecture (FLIP-31)
- Credit-Based Flow Control (FLIP-74)
- Buffer Debloating (FLINK-23560)

#### Code References
- NettyShuffleEnvironmentOptions.java (flink-core)
- NettyConfig.java (flink-runtime)
- StreamNetworkThroughputBenchmark.java (flink-streaming-java/test)

#### Tools
- [Flink Benchmarks](https://github.com/apache/flink-benchmarks)
- [async-profiler](https://github.com/jvm-profiling-tools/async-profiler)
- [Prometheus](https://prometheus.io/)
- [Grafana](https://grafana.com/)

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-10-25 | Claude | Initial testing plan |

---

**End of Netty Optimization Testing Plan**
