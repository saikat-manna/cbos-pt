package com.cbosgroup.cbos.core.flows.repository;

import com.cbosgroup.cbos.core.flows.FlowMetadata;
import com.cbosgroup.cbos.core.flows.FlowStateMetadata;
import com.cbosgroup.cbos.core.flows.ForkJoinState;
import com.cbosgroup.cbos.core.flows.InputField;
import com.cbosgroup.cbos.core.flows.UserTaskState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Repository containing predefined/canned flows.
 * Flows are constants created during repository instantiation.
 */
public class PrebuiltFlowsResposirty {

    public final FlowMetadata SIMPLE_DOCUMENT_COLLECTION;

    public final FlowMetadata KYC_VERIFICATION;

    public final FlowMetadata DOCUMENT_APPROVAL;

    /**
     * Flow with async user tasks - waits for user input
     */
    public final FlowMetadata USER_DOCUMENT_UPLOAD;

    /**
     * Flow with fork-join parallel execution
     */
    public final FlowMetadata PARALLEL_DOCUMENT_COLLECTION;

    public PrebuiltFlowsResposirty() {
        this.SIMPLE_DOCUMENT_COLLECTION = buildSimpleDocumentCollectionFlow();
        this.KYC_VERIFICATION = buildKycVerificationFlow();
        this.DOCUMENT_APPROVAL = buildDocumentApprovalFlow();
        this.USER_DOCUMENT_UPLOAD = buildUserDocumentUploadFlow();
        this.PARALLEL_DOCUMENT_COLLECTION = buildParallelDocumentCollectionFlow();
    }

    private FlowMetadata buildSimpleDocumentCollectionFlow() {
        Map<String, FlowStateMetadata> states = new HashMap<>();

        states.put("start", FlowStateMetadata.builder()
                .stateId("start")
                .stateName("Request Document")
                .description("Request document from user")
                .pausable(true)
                .terminal(false)
                .action(ctx -> "validate")
                .build());

        states.put("validate", FlowStateMetadata.builder()
                .stateId("validate")
                .stateName("Validate Document")
                .description("Validate uploaded document")
                .pausable(false)
                .terminal(false)
                .action(ctx -> "complete")
                .build());

        states.put("complete", FlowStateMetadata.builder()
                .stateId("complete")
                .stateName("Complete")
                .description("Document collection completed")
                .pausable(false)
                .terminal(true)
                .action(ctx -> null)
                .build());

        return FlowMetadata.builder()
                .flowId("simple-document-collection")
                .flowName("Simple Document Collection")
                .description("Collects a single document")
                .states(states)
                .startStateId("start")
                .build();
    }

    private FlowMetadata buildKycVerificationFlow() {
        Map<String, FlowStateMetadata> states = new HashMap<>();

        states.put("collect_id", FlowStateMetadata.builder()
                .stateId("collect_id")
                .stateName("Collect ID")
                .description("Collect government ID")
                .pausable(true)
                .terminal(false)
                .action(ctx -> "collect_address")
                .build());

        states.put("collect_address", FlowStateMetadata.builder()
                .stateId("collect_address")
                .stateName("Collect Address Proof")
                .description("Collect address proof")
                .pausable(true)
                .terminal(false)
                .action(ctx -> "verify")
                .build());

        states.put("verify", FlowStateMetadata.builder()
                .stateId("verify")
                .stateName("Verify")
                .description("Verify documents")
                .pausable(false)
                .terminal(true)
                .action(ctx -> null)
                .build());

        return FlowMetadata.builder()
                .flowId("kyc-verification")
                .flowName("KYC Verification")
                .description("Know Your Customer verification")
                .states(states)
                .startStateId("collect_id")
                .build();
    }

    private FlowMetadata buildDocumentApprovalFlow() {
        Map<String, FlowStateMetadata> states = new HashMap<>();

        states.put("submit", FlowStateMetadata.builder()
                .stateId("submit")
                .stateName("Submit")
                .description("Submit document")
                .pausable(false)
                .terminal(false)
                .action(ctx -> "approve")
                .build());

        states.put("approve", FlowStateMetadata.builder()
                .stateId("approve")
                .stateName("Approve")
                .description("Approval step")
                .pausable(true)
                .terminal(false)
                .action(ctx -> "done")
                .build());

        states.put("done", FlowStateMetadata.builder()
                .stateId("done")
                .stateName("Done")
                .description("Approval complete")
                .pausable(false)
                .terminal(true)
                .action(ctx -> null)
                .build());

        return FlowMetadata.builder()
                .flowId("document-approval")
                .flowName("Document Approval")
                .description("Document approval workflow")
                .states(states)
                .startStateId("submit")
                .build();
    }

    private FlowMetadata buildUserDocumentUploadFlow() {
        Map<String, FlowStateMetadata> states = new HashMap<>();

        // Start state - auto
        states.put("init", FlowStateMetadata.builder()
                .stateId("init")
                .stateName("Initialize")
                .description("Initialize document upload flow")
                .pausable(false)
                .terminal(false)
                .action(ctx -> {
                    ctx.put("flowStarted", true);
                    return "upload_id";
                })
                .build());

        // User task - upload ID document
        states.put("upload_id", UserTaskState.builder()
                .stateId("upload_id")
                .stateName("Upload ID Document")
                .description("Please upload your government-issued ID")
                .pausable(false)
                .terminal(false)
                .expectedInputs(List.of(
                        InputField.builder()
                                .fieldName("idDocument")
                                .label("ID Document")
                                .fieldType(InputField.FieldType.DOCUMENT)
                                .required(true)
                                .description("Government-issued photo ID")
                                .build(),
                        InputField.builder()
                                .fieldName("idType")
                                .label("ID Type")
                                .fieldType(InputField.FieldType.STRING)
                                .required(true)
                                .description("Type of ID (passport, drivers license, etc)")
                                .build()
                ))
                .onResponse((ctx, response) -> {
                    ctx.put("idDocument", response.get("idDocument"));
                    ctx.put("idType", response.get("idType"));
                    return "upload_address_proof";
                })
                .build());

        // User task - upload address proof
        states.put("upload_address_proof", UserTaskState.builder()
                .stateId("upload_address_proof")
                .stateName("Upload Address Proof")
                .description("Please upload proof of address")
                .pausable(false)
                .terminal(false)
                .expectedInputs(List.of(
                        InputField.builder()
                                .fieldName("addressDocument")
                                .label("Address Proof")
                                .fieldType(InputField.FieldType.DOCUMENT)
                                .required(true)
                                .description("Utility bill or bank statement")
                                .build()
                ))
                .onResponse((ctx, response) -> {
                    ctx.put("addressDocument", response.get("addressDocument"));
                    return "validate";
                })
                .build());

        // Auto state - validate
        states.put("validate", FlowStateMetadata.builder()
                .stateId("validate")
                .stateName("Validate Documents")
                .description("System validates uploaded documents")
                .pausable(false)
                .terminal(false)
                .action(ctx -> {
                    ctx.put("validated", true);
                    return "complete";
                })
                .build());

        // Terminal state
        states.put("complete", FlowStateMetadata.builder()
                .stateId("complete")
                .stateName("Complete")
                .description("Document upload completed")
                .pausable(false)
                .terminal(true)
                .action(ctx -> null)
                .build());

        return FlowMetadata.builder()
                .flowId("user-document-upload")
                .flowName("User Document Upload")
                .description("Collects documents from user with async user tasks")
                .states(states)
                .startStateId("init")
                .build();
    }

    private FlowMetadata buildParallelDocumentCollectionFlow() {
        Map<String, FlowStateMetadata> states = new HashMap<>();

        // Start state
        states.put("init", FlowStateMetadata.builder()
                .stateId("init")
                .stateName("Initialize")
                .description("Initialize parallel document collection")
                .pausable(false)
                .terminal(false)
                .action(ctx -> {
                    ctx.put("startTime", System.currentTimeMillis());
                    System.out.println("  [init] Starting parallel document collection...");
                    return "parallel_collect";
                })
                .build());

        // Child state 1: Sync task - ID verification
        FlowStateMetadata verifyIdTask = FlowStateMetadata.builder()
                .stateId("verify_id")
                .stateName("Verify ID")
                .description("Verify government ID")
                .pausable(false)
                .terminal(false)
                .action(ctx -> {
                    System.out.println("  [verify_id] Verifying ID document...");
                    try { Thread.sleep(500); } catch (InterruptedException e) {}
                    ctx.put("idVerified", true);
                    return "verified";
                })
                .build();

        // Child state 2: Sync task - Address verification
        FlowStateMetadata verifyAddressTask = FlowStateMetadata.builder()
                .stateId("verify_address")
                .stateName("Verify Address")
                .description("Verify address proof")
                .pausable(false)
                .terminal(false)
                .action(ctx -> {
                    System.out.println("  [verify_address] Verifying address proof...");
                    try { Thread.sleep(300); } catch (InterruptedException e) {}
                    ctx.put("addressVerified", true);
                    return "verified";
                })
                .build();

        // Child state 3: User task - additional document upload
        FlowStateMetadata additionalDocTask = UserTaskState.builder()
                .stateId("upload_additional")
                .stateName("Upload Additional Document")
                .description("Please upload any additional supporting document")
                .pausable(false)
                .terminal(false)
                .eligibleRoles(Set.of("user", "applicant"))
                .expectedInputs(List.of(
                        InputField.builder()
                                .fieldName("additionalDoc")
                                .label("Additional Document")
                                .fieldType(InputField.FieldType.DOCUMENT)
                                .required(false)
                                .description("Optional supporting document")
                                .build()
                ))
                .onResponse((ctx, response) -> {
                    ctx.put("additionalDoc", response.get("additionalDoc"));
                    return "uploaded";
                })
                .build();

        // Fork-join state
        ForkJoinState forkJoin = new ForkJoinState(
                "parallel_collect",
                "Parallel Document Collection",
                new FlowStateMetadata[]{verifyIdTask, verifyAddressTask, additionalDocTask},
                (ctx, results) -> {
                    System.out.println("  [merge] All parallel tasks completed!");
                    System.out.println("  [merge] Results: " + results);
                    ctx.put("parallelComplete", true);
                    return "finalize";
                }
        );
        states.put("parallel_collect", forkJoin);

        // Final state
        states.put("finalize", FlowStateMetadata.builder()
                .stateId("finalize")
                .stateName("Finalize")
                .description("Finalize document collection")
                .pausable(false)
                .terminal(true)
                .action(ctx -> {
                    long elapsed = System.currentTimeMillis() - (Long) ctx.get("startTime");
                    ctx.put("totalTime", elapsed);
                    System.out.println("  [finalize] Document collection complete in " + elapsed + "ms");
                    return null;
                })
                .build());

        return FlowMetadata.builder()
                .flowId("parallel-document-collection")
                .flowName("Parallel Document Collection")
                .description("Collects and verifies documents in parallel using fork-join")
                .states(states)
                .startStateId("init")
                .build();
    }
}
