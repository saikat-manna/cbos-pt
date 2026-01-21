package com.cbosgroup.cbos.core.flows.repository;

import com.cbosgroup.cbos.core.flows.FlowMetadata;
import com.cbosgroup.cbos.core.flows.FlowStateMetadata;

import java.util.HashMap;
import java.util.Map;

/**
 * Repository containing predefined/canned flows.
 * Flows are constants created during repository instantiation.
 */
public class PrebuiltFlowsResposirty {

    public final FlowMetadata SIMPLE_DOCUMENT_COLLECTION;

    public final FlowMetadata KYC_VERIFICATION;

    public final FlowMetadata DOCUMENT_APPROVAL;

    public PrebuiltFlowsResposirty() {
        this.SIMPLE_DOCUMENT_COLLECTION = buildSimpleDocumentCollectionFlow();
        this.KYC_VERIFICATION = buildKycVerificationFlow();
        this.DOCUMENT_APPROVAL = buildDocumentApprovalFlow();
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
}
