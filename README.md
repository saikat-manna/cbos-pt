# CBOS Flow Engine

A workflow/flow engine built with Spring Boot for orchestrating document collection and verification processes.

## Prerequisites

- Java 16+
- Maven 3.6+

## Project Structure

```
src/main/java/com/cbosgroup/cbos/
├── cli/
│   └── FlowCLITester.java          # CLI tester (Picocli)
├── core/
│   ├── actors/
│   │   ├── Actor.java              # Actor model
│   │   └── Signature.java
│   ├── documents/
│   │   ├── Document.java
│   │   ├── DocumentField.java
│   │   ├── DocumentFieldMetadata.java
│   │   └── DocumentMetadata.java
│   ├── flows/
│   │   ├── FlowExecuter.java       # Core flow execution engine
│   │   ├── FlowMetadata.java       # Flow definition
│   │   ├── FlowStateMetadata.java  # State definition
│   │   ├── FlowExecutionStateData.java
│   │   ├── UserTaskState.java      # Async user task state
│   │   ├── ForkJoinState.java      # Parallel execution state
│   │   ├── InputField.java
│   │   ├── ActorNotifier.java
│   │   ├── repository/
│   │   │   └── PrebuiltFlowsResposirty.java  # Predefined flows
│   │   └── runtime/
│   │       ├── FlowInstance.java
│   │       ├── FlowStateInstance.java
│   │       ├── ForkJoinStateInstance.java
│   │       ├── FlowStateExecutor.java
│   │       └── FlowExecutionHistory.java
│   └── Version.java
├── usermanagement/
│   └── User.java
└── DemoApplication.java            # Spring Boot main
```

## Build Profiles

| Profile | Description | Main Class | Output JAR |
|---------|-------------|------------|------------|
| `app` (default) | Spring Boot application | `DemoApplication` | `cbos-0.0.1-SNAPSHOT.jar` |
| `cli` | Flow CLI tester | `FlowCLITester` | `flow-cli.jar` |

### Build Commands

```bash
# Build Spring Boot app (default profile)
mvn clean package -DskipTests

# Build CLI tester
mvn clean package -Pcli -DskipTests

# Compile only
mvn compile
```

## Running

### Spring Boot Application

```bash
mvn package -DskipTests
java -jar target/cbos-0.0.1-SNAPSHOT.jar
```

### CLI Tester

```bash
mvn package -Pcli -DskipTests
java -jar target/flow-cli.jar [command]
```

#### CLI Commands

```bash
# Show help
java -jar target/flow-cli.jar --help

# List all available flows
java -jar target/flow-cli.jar list

# Run a specific flow
java -jar target/flow-cli.jar run <flow-id> [-v]

# Interactive mode
java -jar target/flow-cli.jar interactive
```

#### Available Flows

| Flow ID | Description | Features |
|---------|-------------|----------|
| `simple-document-collection` | Basic 3-state flow | Pausable states |
| `kyc-verification` | KYC verification flow | Multiple pausable states |
| `document-approval` | Document approval workflow | Approval state |
| `user-document-upload` | Document upload with user tasks | UserTaskState, async input |
| `parallel-document-collection` | Parallel document verification | ForkJoinState, parallel execution |

#### Example: Running the Fork-Join Flow

```bash
$ java -jar target/flow-cli.jar run parallel-document-collection -v

==================================================
Starting flow: Parallel Document Collection
==================================================

[init] Starting parallel document collection...
[verify_id] Verifying ID document...
[verify_address] Verifying address proof...

========================================
[FORK-JOIN] Pending tasks: 1
========================================

[FORK-JOIN USER TASK] Upload Additional Document
Enter Additional Document (optional): my-doc.pdf

[merge] All parallel tasks completed!
[finalize] Document collection complete in 512ms

Flow COMPLETED!
```

## Flow Engine Features

### State Types

- **FlowStateMetadata**: Basic synchronous state with action function
- **UserTaskState**: Async state waiting for user input with validation
- **ForkJoinState**: Parallel execution of child states with merge function

### Flow Status

- `NOT_STARTED`: Flow created but not initialized
- `RUNNING`: Flow is actively executing
- `PAUSED`: Flow paused at a pausable state
- `AWAITING_USER_INPUT`: Waiting for user task response
- `COMPLETED`: Flow finished successfully
- `FAILED`: Flow terminated with error

### Fork-Join Pattern

Execute multiple states in parallel and wait for all to complete:

```java
ForkJoinState forkJoin = new ForkJoinState(
    "parallel_tasks",
    "Parallel Tasks",
    new FlowStateMetadata[]{task1, task2, userTask3},
    (context, results) -> {
        // Merge function called when all complete
        return "next_state";
    }
);
```

- Sync states execute in parallel via thread pool
- UserTaskState children wait for user input
- Pausable children can be resumed individually
- Merge function receives results from all children

## Development

### Adding a New Flow

1. Add flow metadata to `PrebuiltFlowsResposirty`
2. Define states with `FlowStateMetadata.builder()`
3. Use `UserTaskState` for async user input
4. Use `ForkJoinState` for parallel execution
5. Register in the repository constructor

### Running Tests

```bash
mvn test
```
