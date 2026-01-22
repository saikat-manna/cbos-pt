package com.cbosgroup.cbos.cli;

import com.cbosgroup.cbos.core.Version;
import com.cbosgroup.cbos.core.flows.*;
import com.cbosgroup.cbos.core.flows.FlowExecutionStateData.FlowStatus;
import com.cbosgroup.cbos.core.flows.repository.PrebuiltFlowsResposirty;
import com.cbosgroup.cbos.core.flows.runtime.FlowInstance;
import com.cbosgroup.cbos.core.flows.runtime.FlowStateInstance;
import com.cbosgroup.cbos.core.flows.runtime.ForkJoinStateInstance;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.*;
import java.util.concurrent.Callable;

/**
 * CLI tester for the CBOS Flow Engine.
 * Uses Picocli for command-line interface.
 */
@Command(
    name = "flow-cli",
    mixinStandardHelpOptions = true,
    version = "1.0",
    description = "CBOS Flow Engine CLI Tester",
    subcommands = {
        FlowCLITester.ListFlows.class,
        FlowCLITester.RunFlow.class,
        FlowCLITester.Interactive.class
    }
)
public class FlowCLITester implements Callable<Integer> {

    private static final PrebuiltFlowsResposirty flowRepo = new PrebuiltFlowsResposirty();
    private static final FlowExecuter executer = new FlowExecuter();
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        int exitCode = new CommandLine(new FlowCLITester()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        // Default: show help
        CommandLine.usage(this, System.out);
        return 0;
    }

    /**
     * List all available flows
     */
    @Command(name = "list", description = "List all available flows")
    static class ListFlows implements Callable<Integer> {

        @Override
        public Integer call() {
            System.out.println("\n=== Available Flows ===\n");

            List<FlowMetadata> flows = getAvailableFlows();
            for (int i = 0; i < flows.size(); i++) {
                FlowMetadata flow = flows.get(i);
                System.out.printf("  %d. %-30s - %s%n", i + 1, flow.getFlowId(), flow.getDescription());
                printFlowStates(flow);
            }
            return 0;
        }

        private void printFlowStates(FlowMetadata flow) {
            System.out.println("     States:");
            flow.getStates().values().forEach(state -> {
                String type = getStateType(state);
                System.out.printf("       - %-20s [%s]%s%n",
                    state.getStateId(),
                    type,
                    state.isTerminal() ? " (terminal)" : "");
            });
            System.out.println();
        }

        private String getStateType(FlowStateMetadata state) {
            if (state instanceof UserTaskState) return "UserTask";
            if (state instanceof ForkJoinState) return "ForkJoin";
            if (state.isPausable()) return "Pausable";
            return "Auto";
        }
    }

    /**
     * Run a specific flow by ID
     */
    @Command(name = "run", description = "Run a flow by ID")
    static class RunFlow implements Callable<Integer> {

        @Parameters(index = "0", description = "Flow ID to run")
        private String flowId;

        @Option(names = {"-v", "--verbose"}, description = "Verbose output")
        private boolean verbose;

        @Override
        public Integer call() {
            FlowMetadata flow = findFlowById(flowId);
            if (flow == null) {
                System.err.println("Flow not found: " + flowId);
                System.err.println("Use 'flow-cli list' to see available flows.");
                return 1;
            }

            return runFlowInteractively(flow, verbose);
        }
    }

    /**
     * Interactive mode - select and run flows
     */
    @Command(name = "interactive", aliases = {"i"}, description = "Interactive mode")
    static class Interactive implements Callable<Integer> {

        @Override
        public Integer call() {
            System.out.println("\n=== CBOS Flow Engine - Interactive Mode ===\n");

            while (true) {
                System.out.println("Options:");
                System.out.println("  1. List flows");
                System.out.println("  2. Run a flow");
                System.out.println("  3. Exit");
                System.out.print("\nSelect [1-3]: ");

                String choice = scanner.nextLine().trim();
                switch (choice) {
                    case "1" -> new ListFlows().call();
                    case "2" -> selectAndRunFlow();
                    case "3" -> {
                        System.out.println("Goodbye!");
                        return 0;
                    }
                    default -> System.out.println("Invalid choice.");
                }
            }
        }

        private void selectAndRunFlow() {
            List<FlowMetadata> flows = getAvailableFlows();
            System.out.println("\nSelect a flow:");
            for (int i = 0; i < flows.size(); i++) {
                System.out.printf("  %d. %s%n", i + 1, flows.get(i).getFlowName());
            }
            System.out.print("\nEnter number [1-" + flows.size() + "]: ");

            try {
                int idx = Integer.parseInt(scanner.nextLine().trim()) - 1;
                if (idx >= 0 && idx < flows.size()) {
                    runFlowInteractively(flows.get(idx), true);
                } else {
                    System.out.println("Invalid selection.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input.");
            }
        }
    }

    // ========== Helper Methods ==========

    static List<FlowMetadata> getAvailableFlows() {
        return List.of(
            flowRepo.SIMPLE_DOCUMENT_COLLECTION,
            flowRepo.KYC_VERIFICATION,
            flowRepo.DOCUMENT_APPROVAL,
            flowRepo.USER_DOCUMENT_UPLOAD,
            flowRepo.PARALLEL_DOCUMENT_COLLECTION
        );
    }

    static FlowMetadata findFlowById(String flowId) {
        return getAvailableFlows().stream()
            .filter(f -> f.getFlowId().equals(flowId))
            .findFirst()
            .orElse(null);
    }

    static int runFlowInteractively(FlowMetadata flow, boolean verbose) {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("Starting flow: " + flow.getFlowName());
        System.out.println("=".repeat(50) + "\n");

        String instanceId = executer.runFlow(flow, Version.builder().major(1).minor(0).build());
        FlowInstance instance = executer.getFlowInstance(instanceId);

        // Main interaction loop
        while (true) {
            FlowStatus status = executer.getFlowStatus(instanceId);

            if (status == FlowStatus.COMPLETED) {
                printSuccess("Flow COMPLETED!");
                if (verbose) {
                    printContext(instance.getContext());
                }
                return 0;
            }

            if (status == FlowStatus.FAILED) {
                printError("Flow FAILED!");
                return 1;
            }

            if (status == FlowStatus.AWAITING_USER_INPUT) {
                handleUserTask(instanceId, instance);
                continue;
            }

            if (status == FlowStatus.PAUSED) {
                FlowStateInstance currentState = instance.getCurrentState();

                // Check if this is a fork-join with pending user tasks
                if (currentState instanceof ForkJoinStateInstance forkInstance) {
                    if (forkInstance.hasPendingChildren()) {
                        handleForkJoinPendingTasks(instanceId, forkInstance);
                        continue;
                    }
                }

                System.out.println("\n[PAUSED] Flow is paused at: " + instance.getExecutionData().getCurrentStateId());
                System.out.print("Press Enter to resume or 'q' to quit: ");
                String input = scanner.nextLine().trim();
                if ("q".equalsIgnoreCase(input)) {
                    return 0;
                }
                executer.resumeFlow(instanceId);
                continue;
            }

            // If running, wait a bit and check again
            if (status == FlowStatus.RUNNING) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return 1;
                }
            }
        }
    }

    static void handleUserTask(String instanceId, FlowInstance instance) {
        UserTaskState userTask = executer.getPendingUserTask(instanceId);
        if (userTask == null) {
            printError("No pending user task found!");
            return;
        }

        System.out.println("\n" + "-".repeat(40));
        System.out.println("[USER TASK] " + userTask.getStateName());
        System.out.println(userTask.getDescription());
        System.out.println("-".repeat(40));

        Map<String, Object> response = collectUserTaskResponse(userTask);

        try {
            executer.submitUserResponse(instanceId, response);
            printSuccess("Response submitted!");
        } catch (IllegalArgumentException e) {
            printError("Validation failed: " + e.getMessage());
        }
    }

    static void handleForkJoinPendingTasks(String instanceId, ForkJoinStateInstance forkInstance) {
        Map<String, FlowStateInstance> pending = forkInstance.getChildExecutionStates();

        System.out.println("\n" + "=".repeat(40));
        System.out.println("[FORK-JOIN] Pending tasks: " + pending.size());
        System.out.println("=".repeat(40));

        // Process each pending child
        List<String> pendingIds = new ArrayList<>(pending.keySet());
        for (String childStateId : pendingIds) {
            FlowStateInstance childInstance = pending.get(childStateId);
            FlowStateMetadata childMeta = childInstance.getMetadata();

            if (childMeta instanceof UserTaskState userTask) {
                System.out.println("\n" + "-".repeat(40));
                System.out.println("[FORK-JOIN USER TASK] " + userTask.getStateName());
                System.out.println(userTask.getDescription());
                System.out.println("-".repeat(40));

                Map<String, Object> response = collectUserTaskResponse(userTask);

                try {
                    executer.submitForkUserResponse(instanceId, childStateId, response);
                    printSuccess("Fork-join task '" + childStateId + "' completed!");
                } catch (IllegalArgumentException e) {
                    printError("Validation failed: " + e.getMessage());
                }
            } else if (childMeta.isPausable()) {
                System.out.println("\n[FORK-JOIN PAUSED] Child '" + childStateId + "' is paused.");
                System.out.print("Press Enter to resume: ");
                scanner.nextLine();
                executer.resumeForkChild(instanceId, childStateId);
                printSuccess("Fork-join child '" + childStateId + "' resumed!");
            }
        }
    }

    static Map<String, Object> collectUserTaskResponse(UserTaskState userTask) {
        Map<String, Object> response = new HashMap<>();

        if (userTask.getExpectedInputs() != null) {
            System.out.println("\nRequired inputs:");
            for (InputField field : userTask.getExpectedInputs()) {
                System.out.printf("  - %s (%s%s): %s%n",
                    field.getFieldName(),
                    field.getFieldType(),
                    field.isRequired() ? ", required" : "",
                    field.getDescription() != null ? field.getDescription() : "");
            }
            System.out.println();

            for (InputField field : userTask.getExpectedInputs()) {
                String value = promptForInput(field);
                response.put(field.getFieldName(), convertValue(value, field.getFieldType()));
            }
        } else {
            System.out.print("\nPress Enter to continue: ");
            scanner.nextLine();
        }

        return response;
    }

    static String promptForInput(InputField field) {
        String prompt = String.format("Enter %s", field.getLabel());
        if (!field.isRequired()) {
            prompt += " (optional)";
        }
        System.out.print(prompt + ": ");
        return scanner.nextLine().trim();
    }

    static Object convertValue(String value, InputField.FieldType type) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return switch (type) {
            case NUMBER -> {
                try {
                    yield Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    yield value;
                }
            }
            case BOOLEAN -> Boolean.parseBoolean(value);
            case DATE -> value; // Keep as string for now
            default -> value;
        };
    }

    static void printContext(Map<String, Object> context) {
        System.out.println("\nFinal context:");
        context.forEach((k, v) -> System.out.printf("  %s: %s%n", k, v));
    }

    static void printSuccess(String msg) {
        System.out.println("\n\u001B[32m" + msg + "\u001B[0m");
    }

    static void printError(String msg) {
        System.err.println("\n\u001B[31m" + msg + "\u001B[0m");
    }
}
