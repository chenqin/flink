# Flink Runtime Configuration Scan

**Generated:** 2025-10-25
**Purpose:** Comprehensive scan and documentation of runtime configurations in Apache Flink

## Executive Summary

This document provides a complete scan of runtime configurations in Apache Flink, covering configuration structure, key options, patterns, and locations. The scan identified **31 configuration option files** and numerous runtime configuration assembly classes.

---

## 1. Configuration Architecture

### 1.1 Core Configuration Framework

**Location:** `/home/user/flink/flink-core/src/main/java/org/apache/flink/configuration/`

#### Key Infrastructure Classes:
- **ConfigOptions.java** - Builder API for creating ConfigOption instances
- **ConfigOption.java** - ConfigOption definition interface with type safety
- **Configuration.java** - Main configuration container class
- **ReadableConfig.java** - Read-only configuration interface
- **GlobalConfiguration.java** - Global configuration access point
- **ConfigurationUtils.java** - Configuration utility methods

### 1.2 Configuration Definition Pattern

All configuration options follow this standard pattern:

```java
public static final ConfigOption<Type> OPTION_NAME =
        ConfigOptions.key("config.key.name")
                .typeMethod()        // .stringType(), .intType(), .durationType(), etc.
                .defaultValue(value)  // or .noDefaultValue()
                .withDescription("Description")
                .withDeprecatedKeys("old.key.name");
```

**Example from ExecutionOptions.java:40-46:**
```java
public static final ConfigOption<RuntimeExecutionMode> RUNTIME_MODE =
        ConfigOptions.key("execution.runtime-mode")
                .enumType(RuntimeExecutionMode.class)
                .defaultValue(RuntimeExecutionMode.STREAMING)
                .withDescription(
                        "Runtime execution mode of DataStream programs. Among other things, "
                                + "this controls task scheduling, network shuffle behavior, and time semantics.");
```

---

## 2. Runtime Configuration Categories

### 2.1 Execution Options
**File:** `ExecutionOptions.java`

| Configuration Key | Type | Default | Description |
|-------------------|------|---------|-------------|
| `execution.runtime-mode` | Enum | STREAMING | Runtime execution mode (STREAMING/BATCH) |
| `execution.batch-shuffle-mode` | Enum | ALL_EXCHANGES_BLOCKING | Batch shuffle mode (BLOCKING/PIPELINED/HYBRID) |
| `execution.buffer-timeout.enabled` | Boolean | true | Enable/disable buffer timeout |
| `execution.buffer-timeout.interval` | Duration | 100ms | Buffer flush frequency |
| `execution.checkpointing.snapshot-compression` | Boolean | false | Enable snapshot compression |
| `execution.sorted-inputs.enabled` | Boolean | true | Enable sorted inputs for keyed operators (BATCH only) |
| `execution.sorted-inputs.memory` | MemorySize | 128MB | Memory for sorting inputs |
| `execution.batch-state-backend.enabled` | Boolean | true | Enable batch-specific state backend |

**Special Constants:**
- `DISABLED_NETWORK_BUFFER_TIMEOUT = -1L`
- `FLUSH_AFTER_EVERY_RECORD = 0L`

### 2.2 TaskManager Options
**File:** `TaskManagerOptions.java`

#### General Settings (Lines 88-332)

| Configuration Key | Type | Default | Description |
|-------------------|------|---------|-------------|
| `taskmanager.jvm-exit-on-oom` | Boolean | false | Kill TaskManager on OutOfMemoryError |
| `taskmanager.host` | String | (none) | External network address |
| `taskmanager.bind-host` | String | 0.0.0.0 | Local bind address |
| `taskmanager.rpc.port` | String | "0" | RPC port (0=find free port) |
| `taskmanager.rpc.bind-port` | Integer | (none) | Local RPC bind port |
| `taskmanager.registration.timeout` | Duration | 5 min | Registration timeout |
| `taskmanager.numberOfTaskSlots` | Integer | 1 | Number of task slots |
| `taskmanager.slot.timeout` | Duration | (fallback to akka timeout) | Slot inactivity timeout |
| `taskmanager.network.bind-policy` | String | "ip" | Address binding policy (ip/name) |
| `taskmanager.resource-id` | String | (auto-generated) | TaskManager ResourceID |

#### Memory Configuration (Lines 363-654)

| Configuration Key | Type | Default | Description |
|-------------------|------|---------|-------------|
| `taskmanager.memory.process.size` | MemorySize | (none) | Total process memory |
| `taskmanager.memory.flink.size` | MemorySize | (none) | Total Flink memory |
| `taskmanager.memory.framework.heap.size` | MemorySize | 128m | Framework heap memory |
| `taskmanager.memory.framework.off-heap.size` | MemorySize | 128m | Framework off-heap memory |
| `taskmanager.memory.task.heap.size` | MemorySize | (derived) | Task heap memory |
| `taskmanager.memory.task.off-heap.size` | MemorySize | 0 | Task off-heap memory |
| `taskmanager.memory.managed.size` | MemorySize | (derived) | Managed memory size |
| `taskmanager.memory.managed.fraction` | Float | 0.4 | Managed memory fraction |
| `taskmanager.memory.managed.consumer-weights` | Map | OPERATOR:70, STATE_BACKEND:70, PYTHON:30 | Consumer weight distribution |
| `taskmanager.memory.network.min` | MemorySize | 64m | Minimum network memory |
| `taskmanager.memory.network.max` | MemorySize | MAX_VALUE | Maximum network memory |
| `taskmanager.memory.network.fraction` | Float | 0.1 | Network memory fraction |
| `taskmanager.memory.segment-size` | MemorySize | 32kb | Memory segment size |
| `taskmanager.memory.min-segment-size` | MemorySize | 256b | Minimum segment size |
| `taskmanager.memory.jvm-metaspace.size` | MemorySize | 256m | JVM metaspace size |
| `taskmanager.memory.jvm-overhead.min` | MemorySize | 192m | Min JVM overhead |
| `taskmanager.memory.jvm-overhead.max` | MemorySize | 1g | Max JVM overhead |
| `taskmanager.memory.jvm-overhead.fraction` | Float | 0.1 | JVM overhead fraction |

#### Network/Buffer Configuration (Lines 533-603)

| Configuration Key | Type | Default | Description |
|-------------------|------|---------|-------------|
| `taskmanager.network.memory.buffer-debloat.enabled` | Boolean | false | Enable automatic buffer debloating |
| `taskmanager.network.memory.buffer-debloat.period` | Duration | 200ms | Buffer size recalculation period |
| `taskmanager.network.memory.buffer-debloat.samples` | Integer | 20 | Number of samples for adjustment |
| `taskmanager.network.memory.buffer-debloat.target` | Duration | 1s | Target buffer consumption time |
| `taskmanager.network.memory.buffer-debloat.threshold-percentages` | Integer | 25 | Minimum change threshold (%) |
| `taskmanager.memory.framework.off-heap.batch-shuffle.size` | MemorySize | 64m | Batch shuffle read memory |

#### Task Cancellation (Lines 660-701)

| Configuration Key | Type | Default | Description |
|-------------------|------|---------|-------------|
| `task.cancellation.interval` | Long | 30000ms | Interval between cancellation attempts |
| `task.cancellation.timeout` | Long | 180000ms | Cancellation timeout |
| `task.cancellation.timers.timeout` | Long | 7500ms | Timer shutdown timeout |

### 2.3 JobManager Options
**File:** `JobManagerOptions.java`

#### Network Settings (Lines 55-116)

| Configuration Key | Type | Default | Description |
|-------------------|------|---------|-------------|
| `jobmanager.rpc.address` | String | (none) | JobManager network address |
| `jobmanager.rpc.port` | Integer | 6123 | JobManager RPC port |
| `jobmanager.bind-host` | String | 0.0.0.0 | Local bind address |
| `jobmanager.rpc.bind-port` | Integer | (none) | Local RPC bind port |

#### Memory Configuration (Lines 145-254)

| Configuration Key | Type | Default | Description |
|-------------------|------|---------|-------------|
| `jobmanager.memory.process.size` | MemorySize | (none) | Total process memory |
| `jobmanager.memory.flink.size` | MemorySize | (none) | Total Flink memory |
| `jobmanager.memory.heap.size` | MemorySize | (none) | JVM heap size (min 128MB) |
| `jobmanager.memory.off-heap.size` | MemorySize | 128m | Off-heap memory size |
| `jobmanager.memory.enable-jvm-direct-memory-limit` | Boolean | false | Enable JVM direct memory limit |
| `jobmanager.memory.jvm-metaspace.size` | MemorySize | 256m | JVM metaspace size |
| `jobmanager.memory.jvm-overhead.min` | MemorySize | 192m | Min JVM overhead |
| `jobmanager.memory.jvm-overhead.max` | MemorySize | 1g | Max JVM overhead |
| `jobmanager.memory.jvm-overhead.fraction` | Float | 0.1 | JVM overhead fraction |

#### Execution & Scheduling (Lines 256-547)

| Configuration Key | Type | Default | Description |
|-------------------|------|---------|-------------|
| `jobmanager.execution.attempts-history-size` | Integer | 16 | Max historical execution attempts |
| `jobmanager.failure-enrichers` | String | (none) | Failure enricher plugins list |
| `jobmanager.execution.failover-strategy` | String | "region" | Failover strategy (full/region) |
| `jobmanager.archive.fs.dir` | String | (none) | Archive directory for completed jobs |
| `jobstore.cache-size` | Long | 50MB | Job store cache size |
| `jobstore.expiration-time` | Long | 3600s | Job expiration time |
| `jobstore.max-capacity` | Integer | MAX_VALUE | Max completed jobs in store |
| `jobstore.type` | Enum | File | Job store type (File/Memory) |
| `jobmanager.retrieve-taskmanager-hostname` | Boolean | true | Retrieve TaskManager hostname |
| `jobmanager.future-pool.size` | Integer | (CPU cores) | Future thread pool size |
| `jobmanager.io-pool.size` | Integer | (CPU cores) | IO thread pool size |
| `slot.request.timeout` | Long | 300000ms | Slot request timeout |
| `slot.idle.timeout` | Long | (heartbeat timeout) | Idle slot timeout |
| `jobmanager.scheduler` | Enum | Default | Scheduler type (Default/Adaptive/AdaptiveBatch) |
| `scheduler-mode` | Enum | (none) | Scheduler mode (supports REACTIVE) |
| `jobmanager.adaptive-scheduler.min-parallelism-increase` | Integer | 1 | Min parallelism increase for scale-up |
| `jobmanager.adaptive-scheduler.resource-wait-timeout` | Duration | 5 min | Resource acquisition timeout |
| `jobmanager.adaptive-scheduler.resource-stabilization-timeout` | Duration | 10s | Resource stabilization wait time |
| `jobmanager.partition.release-during-job-execution` | Boolean | true | Release partitions during execution |
| `jobmanager.resource-id` | String | (auto-generated) | JobManager ResourceID |
| `jobmanager.partition.hybrid.partition-data-consume-constraint` | Enum | (none) | Hybrid partition consume constraint |

### 2.4 Network/Shuffle Options
**File:** `NettyShuffleEnvironmentOptions.java`

| Configuration Key | Type | Default | Description |
|-------------------|------|---------|-------------|
| `taskmanager.data.port` | Integer | 0 | Data transfer port |
| `taskmanager.data.bind-port` | Integer | (none) | Data bind port |
| `taskmanager.data.ssl.enabled` | Boolean | true | Enable SSL for data transport |
| `taskmanager.network.batch-shuffle.compression.enabled` | Boolean | true | Enable batch shuffle compression |
| `taskmanager.network.compression.codec` | String | "LZ4" | Compression codec (LZ4/LZO/ZSTD) |

### 2.5 Checkpointing Options
**File:** `CheckpointingOptions.java`

| Configuration Key | Type | Default | Description |
|-------------------|------|---------|-------------|
| `state.backend.type` | String | (none) | State backend type (deprecated) |
| `state.checkpoint-storage` | String | (none) | Checkpoint storage implementation |

---

## 3. Runtime Configuration Assembly Classes

### 3.1 TaskManager Configuration
**Location:** `/home/user/flink/flink-runtime/src/main/java/org/apache/flink/runtime/taskexecutor/`

- **TaskManagerConfiguration.java** - Main configuration aggregation
- **TaskManagerServicesConfiguration.java** - Services configuration
- **TaskExecutorMemoryConfiguration.java** - Memory configuration with JSON support
- **QueryableStateConfiguration.java** - Queryable state configuration

### 3.2 Network Configuration
**Location:** `/home/user/flink/flink-runtime/src/main/java/org/apache/flink/runtime/`

- **io/network/netty/NettyConfig.java** - Netty stack configuration
- **taskmanager/NettyShuffleEnvironmentConfiguration.java** - Shuffle environment
- **throughput/BufferDebloatConfiguration.java** - Buffer debloat configuration

### 3.3 Job Management Configuration
**Location:** `/home/user/flink/flink-runtime/src/main/java/org/apache/flink/runtime/`

- **jobmaster/JobMasterConfiguration.java** - JobMaster configuration
- **jobgraph/tasks/CheckpointCoordinatorConfiguration.java** - Checkpoint coordinator
- **jobgraph/SavepointConfigOptions.java** - Savepoint restoration

### 3.4 Cluster & Entry Point Configuration
**Location:** `/home/user/flink/flink-runtime/src/main/java/org/apache/flink/runtime/`

- **entrypoint/ClusterConfiguration.java** - Cluster entry point config
- **entrypoint/EntrypointClusterConfiguration.java** - Base entrypoint config
- **minicluster/MiniClusterConfiguration.java** - Mini cluster for testing

### 3.5 REST Configuration
**Location:** `/home/user/flink/flink-runtime/src/main/java/org/apache/flink/runtime/rest/`

- **RestServerEndpointConfiguration.java** - REST server endpoint config
- **RestClientConfiguration.java** - REST client config
- **handler/RestHandlerConfiguration.java** - REST handler config

---

## 4. Configuration Patterns & Annotations

### 4.1 @ConfigGroups Annotation Pattern

Groups related configurations with common key prefix:

```java
@ConfigGroups(groups = @ConfigGroup(name = "TaskManagerMemory", keyPrefix = "taskmanager.memory"))
public class TaskManagerOptions { ... }
```

**Example from RestartStrategyOptions:**
```java
@ConfigGroups(groups = {
    @ConfigGroup(name = "ExponentialDelayRestartStrategy", keyPrefix = "restart-strategy.exponential-delay"),
    @ConfigGroup(name = "FixedDelayRestartStrategy", keyPrefix = "restart-strategy.fixed-delay"),
    @ConfigGroup(name = "FailureRateRestartStrategy", keyPrefix = "restart-strategy.failure-rate")
})
```

### 4.2 @Documentation.Section Annotation

Categorizes options for documentation:

```java
@Documentation.Section({
    Documentation.Sections.COMMON_HOST_PORT,
    Documentation.Sections.ALL_TASK_MANAGER
})
```

### 4.3 Configuration Assembly Pattern

Factory methods to create configuration from ConfigOptions:

```java
public static TaskManagerConfiguration fromConfiguration(Configuration config) {
    Time rpcTimeout = Time.fromDuration(configuration.get(AkkaOptions.ASK_TIMEOUT_DURATION));
    Duration slotTimeout = configuration.get(TaskManagerOptions.SLOT_TIMEOUT);
    // ... assemble configuration from ConfigOptions
    return new TaskManagerConfiguration(...);
}
```

---

## 5. Complete Configuration Files List

### 5.1 Core Options (31 files in flink-core/src/main/java/org/apache/flink/configuration/)

1. **ExecutionOptions.java** - Execution runtime modes and buffer settings
2. **TaskManagerOptions.java** - TaskManager and task settings
3. **JobManagerOptions.java** - JobManager configuration
4. **NettyShuffleEnvironmentOptions.java** - Network/shuffle configuration
5. **CheckpointingOptions.java** - Checkpoint and state configuration
6. **StateBackendOptions.java** - State backend selection
7. **StateChangelogOptions.java** - State changelog configuration
8. **BatchExecutionOptions.java** - Batch-specific execution modes
9. **RestartStrategyOptions.java** - Restart strategy configuration
10. **CoreOptions.java** - Core class loading and plugin options
11. **ClusterOptions.java** - General cluster settings
12. **HighAvailabilityOptions.java** - HA configuration
13. **ResourceManagerOptions.java** - Resource Manager configuration
14. **HeartbeatManagerOptions.java** - Heartbeat timing configuration
15. **RestOptions.java** - REST API endpoint configuration
16. **WebOptions.java** - Web UI configuration
17. **MetricOptions.java** - Metrics configuration
18. **SecurityOptions.java** - Security-related options
19. **DeploymentOptions.java** - Deployment-specific options
20. **HistoryServerOptions.java** - History server configuration
21. **QueryableStateOptions.java** - Queryable state configuration
22. **PipelineOptions.java** - Pipeline configuration
23. **OptimizerOptions.java** - Optimizer settings
24. **AkkaOptions.java** - Akka configuration (deprecated)
25. **ClientOptions.java** - Client configuration
26. **TableConfigOptions.java** - Table API configuration
27. **YarnConfigOptions.java** - YARN deployment options
28. **KubernetesConfigOptions.java** - Kubernetes deployment options
29. **MesosOptions.java** - Mesos deployment options
30. **RocksDBOptions.java** - RocksDB state backend options
31. **AlgorithmOptions.java** - Algorithm-specific options

### 5.2 Runtime Configuration Classes

Located in `/home/user/flink/flink-runtime/src/main/java/org/apache/flink/runtime/`

#### TaskExecutor (4 files)
- taskexecutor/TaskManagerConfiguration.java
- taskexecutor/TaskManagerServicesConfiguration.java
- taskexecutor/TaskExecutorMemoryConfiguration.java
- taskexecutor/QueryableStateConfiguration.java

#### Network (3 files)
- io/network/netty/NettyConfig.java
- taskmanager/NettyShuffleEnvironmentConfiguration.java
- throughput/BufferDebloatConfiguration.java

#### JobMaster (3 files)
- jobmaster/JobMasterConfiguration.java
- jobgraph/tasks/CheckpointCoordinatorConfiguration.java
- jobgraph/SavepointConfigOptions.java

#### Resource Manager (2 files)
- resourcemanager/ResourceManagerRuntimeServicesConfiguration.java
- resourcemanager/slotmanager/SlotManagerConfiguration.java

#### Entry Points (3 files)
- entrypoint/ClusterConfiguration.java
- entrypoint/EntrypointClusterConfiguration.java
- minicluster/MiniClusterConfiguration.java

#### REST (3 files)
- rest/RestServerEndpointConfiguration.java
- rest/RestClientConfiguration.java
- rest/handler/RestHandlerConfiguration.java

#### Additional (3+ files)
- shuffle/ShuffleServiceOptions.java
- highavailability/JobResultStoreOptions.java
- registration/RetryingRegistrationConfiguration.java

---

## 6. Key Configuration Insights

### 6.1 Memory Model

Flink has a sophisticated memory model with clear separation:

**TaskManager Memory Hierarchy:**
```
Total Process Memory
├── Total Flink Memory
│   ├── Framework Heap (128m default)
│   ├── Framework Off-Heap (128m default)
│   ├── Task Heap (derived)
│   ├── Task Off-Heap (0 default)
│   ├── Managed Memory (40% of Flink memory)
│   └── Network Memory (10% of Flink memory, 64m-MAX)
├── JVM Metaspace (256m default)
└── JVM Overhead (10% of process, 192m-1g)
```

**JobManager Memory Hierarchy:**
```
Total Process Memory
├── Total Flink Memory
│   ├── Heap Memory (configurable, min 128m)
│   └── Off-Heap Memory (128m default)
├── JVM Metaspace (256m default)
└── JVM Overhead (10% of process, 192m-1g)
```

### 6.2 Execution Modes

Flink supports two primary runtime modes:
- **STREAMING** - Default mode with pipelined execution
- **BATCH** - Batch mode with blocking/pipelined/hybrid shuffles

### 6.3 Scheduler Types

Three scheduler implementations available:
- **Default** - Traditional scheduler
- **Adaptive** - Adaptive scheduler for streaming
- **AdaptiveBatch** - Adaptive batch scheduler with auto-parallelism

### 6.4 Configuration Validation

Most configuration classes implement:
- Type-safe ConfigOption definitions
- Default value specifications
- Deprecation handling with fallback keys
- Rich descriptions for documentation
- Validation during configuration loading

---

## 7. Documentation Generation

**Generator Location:**
`/home/user/flink/flink-docs/src/main/java/org/apache/flink/docs/configuration/ConfigOptionsDocGenerator.java`

This tool automatically generates configuration documentation from:
- ConfigOption annotations
- @Documentation.Section annotations
- @ConfigGroups annotations
- Description builders

**Documentation Location:**
- `/home/user/flink/docs/content/docs/deployment/config.md`
- `/home/user/flink/docs/content/docs/dev/datastream/execution/execution_configuration.md`

---

## 8. Best Practices Observed

1. **Type Safety** - All configurations use strongly-typed ConfigOption
2. **Default Values** - Sensible defaults provided for most options
3. **Deprecation Support** - Smooth migration with withDeprecatedKeys()
4. **Documentation** - Rich descriptions with examples
5. **Grouping** - Logical grouping with @ConfigGroups
6. **Validation** - Factory methods validate configuration assembly
7. **Immutability** - Configuration objects are typically immutable
8. **Testability** - Configuration classes support unit testing

---

## 9. Critical Runtime Configurations Summary

### High-Impact Settings:

**Execution:**
- `execution.runtime-mode` - Core execution paradigm
- `execution.buffer-timeout.enabled/interval` - Latency vs throughput tradeoff

**Memory:**
- `taskmanager.memory.process.size` - Total TaskManager memory
- `taskmanager.memory.managed.fraction` - State/operator memory allocation
- `taskmanager.numberOfTaskSlots` - Parallelism capacity

**Network:**
- `taskmanager.network.memory.buffer-debloat.enabled` - Automatic buffer tuning
- `taskmanager.network.batch-shuffle.compression.enabled` - Compression tradeoff

**Scheduling:**
- `jobmanager.scheduler` - Scheduler implementation
- `jobmanager.execution.failover-strategy` - Recovery behavior

**Reliability:**
- `jobmanager.execution.attempts-history-size` - Execution tracking
- `task.cancellation.timeout` - Task termination behavior

---

## 10. Recommendations

### For Production Deployments:

1. **Set explicit memory limits** - Don't rely on derived values in production
2. **Configure buffer debloating** - Enable for variable workload patterns
3. **Tune checkpoint storage** - Configure appropriate state backend
4. **Set appropriate timeouts** - Align with job characteristics
5. **Enable monitoring** - Configure metrics and logging options
6. **Use adaptive scheduler** - For dynamic resource environments
7. **Configure HA properly** - Set up high availability options
8. **Optimize network settings** - Tune buffer sizes and compression

### For Development:

1. **Use MiniCluster** - Leverage minicluster configuration for testing
2. **Enable debug logging** - Use taskmanager.debug.memory.log
3. **Start with defaults** - Only override what's necessary
4. **Test memory configurations** - Validate before production deployment

---

## Appendix A: Configuration Type System

Flink supports these ConfigOption types:

- `stringType()` - String values
- `intType()` - Integer values
- `longType()` - Long values
- `floatType()` - Float values
- `doubleType()` - Double values
- `booleanType()` - Boolean values
- `durationType()` - Duration values (with time units)
- `memoryType()` - MemorySize values (with size units)
- `enumType(Class)` - Enum values
- `mapType()` - Map<String, String> values
- `listType()` - List values

---

## Appendix B: Useful Configuration Commands

### View Current Configuration:
```bash
# Display effective configuration
bin/flink run -m localhost:8081 -p -d <job>

# Check configuration in web UI
http://localhost:8081/#/overview
```

### Configuration File Locations:
- `conf/flink-conf.yaml` - Main configuration file
- `conf/log4j.properties` - Logging configuration
- `conf/masters` - JobManager hosts
- `conf/workers` - TaskManager hosts (formerly slaves)

---

**End of Runtime Configuration Scan**
