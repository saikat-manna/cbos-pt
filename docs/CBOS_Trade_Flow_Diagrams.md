# CBOS Trade Flow Diagrams

## 1. End-to-End Trade Flow (Swim Lane View)

Shows the overall trade journey across all roles and phases, with documents as they participate in each phase.

```mermaid
graph LR
    subgraph TRADE_LIFECYCLE["TRADE LIFECYCLE"]
        direction LR
        T1((INITIATED)) --> T2((CONTRACTED))
        T2 --> T3((SHIPPED))
        T3 --> T4((DOCS_PRESENTED))
        T4 --> T5((SETTLED))
        T5 --> T6((CLOSED))
    end
```

```mermaid
flowchart TB
    subgraph PHASE_1["Phase 1: NEGOTIATION"]
        direction TB
        PI[/"1. Proforma Invoice (PI)"/]
        PO[/"2. Purchase Order / Contract"/]
    end

    subgraph PHASE_2["Phase 2: CONTRACTING"]
        direction TB
        LC[/"3. Letter of Credit (LC)"/]
    end

    subgraph PHASE_3["Phase 3: PRE-SHIPMENT"]
        direction TB
        CI[/"4. Commercial Invoice (CI)"/]
        PL[/"5. Packing List (PL)"/]
        SI[/"6. Shipping Instructions (SI)"/]
    end

    subgraph PHASE_4["Phase 4: SHIPMENT & LOGISTICS"]
        direction TB
        BL[/"7. Bill of Lading / AWB"/]
        COO[/"8. Certificate of Origin"/]
        EXD[/"9. Export Declaration"/]
    end

    subgraph PHASE_5["Phase 5: IMPORT & CLEARANCE"]
        direction TB
        IMD[/"10. Import Declaration"/]
    end

    subgraph PHASE_6["Phase 6: SETTLEMENT & CLOSURE"]
        direction TB
        PA[/"11. Payment Advice"/]
        FIRC[/"12. FIRC"/]
    end

    %% Document Dependencies
    PI -->|"converts to"| CI
    PO -->|"validates"| CI
    LC -->|"rules drive"| CI
    CI -->|"feeds"| PL
    CI -->|"feeds"| SI
    CI -->|"feeds"| EXD
    SI -->|"generates"| BL
    PL -->|"validates against"| BL
    CI -->|"presented with"| BL
    BL -->|"used for"| IMD
    COO -->|"used for"| IMD
    CI -->|"settlement ref"| PA
    LC -->|"settlement via"| PA
    PA -->|"triggers"| FIRC
    EXD -->|"closed by"| FIRC

    style PHASE_1 fill:#E3F2FD,stroke:#1565C0
    style PHASE_2 fill:#E8F5E9,stroke:#2E7D32
    style PHASE_3 fill:#FFF3E0,stroke:#E65100
    style PHASE_4 fill:#F3E5F5,stroke:#6A1B9A
    style PHASE_5 fill:#E0F7FA,stroke:#00695C
    style PHASE_6 fill:#FCE4EC,stroke:#AD1457
```

---

## 2. Document Dependency Graph

Shows how each document feeds into or validates other documents.

```mermaid
flowchart LR
    PI((PI)) -->|converts to| CI((CI))
    PO((PO)) -->|master terms validate| CI
    LC((LC)) -->|rules constrain| CI
    LC -->|required docs list| BL((BL))
    LC -->|required docs list| PL((PL))
    LC -->|required docs list| COO((COO))

    CI -->|line items feed| PL
    CI -->|commodity desc feeds| SI((SI))
    CI -->|values feed| EXD((EXD))
    CI -->|settlement reference| PA((PA))

    SI -->|generates| BL
    PL -->|weights validate against| BL

    BL -->|import clearance| IMD((IMD))
    CI -->|import clearance| IMD
    COO -->|tariff benefits| IMD

    PA -->|triggers| FIRC((FIRC))
    EXD -->|closed by| FIRC

    style PI fill:#BBDEFB,stroke:#1565C0,color:#000
    style PO fill:#BBDEFB,stroke:#1565C0,color:#000
    style LC fill:#C8E6C9,stroke:#2E7D32,color:#000
    style CI fill:#FFE0B2,stroke:#E65100,color:#000
    style PL fill:#FFE0B2,stroke:#E65100,color:#000
    style SI fill:#FFE0B2,stroke:#E65100,color:#000
    style BL fill:#E1BEE7,stroke:#6A1B9A,color:#000
    style COO fill:#E1BEE7,stroke:#6A1B9A,color:#000
    style EXD fill:#E1BEE7,stroke:#6A1B9A,color:#000
    style IMD fill:#B2EBF2,stroke:#00695C,color:#000
    style PA fill:#F8BBD0,stroke:#AD1457,color:#000
    style FIRC fill:#F8BBD0,stroke:#AD1457,color:#000
```

---

## 3. Role Participation Matrix (Swim Lane)

Shows which role is responsible for each action across the trade lifecycle.

```mermaid
sequenceDiagram
    participant A as Exporter (A)
    participant B as Importer (B)
    participant C as AD Bank (C)
    participant D as Forwarder (D)
    participant S as System (CBOS)

    rect rgb(227, 242, 253)
        Note over A,S: Phase 1: NEGOTIATION
        A->>S: Create PI (Draft)
        A->>S: Approve PI
        A->>B: Share PI
        B->>S: Accept / Return PI
        B->>S: Create PO / Contract
        A->>S: Accept PO terms
    end

    rect rgb(232, 245, 233)
        Note over A,S: Phase 2: CONTRACTING (Optional)
        B->>C: Request LC
        C->>S: Issue LC
        C->>A: Advise LC
        A->>S: Accept LC / Request Amendment
        S->>S: Capture LC rules
    end

    rect rgb(255, 243, 224)
        Note over A,S: Phase 3: PRE-SHIPMENT
        A->>S: Create CI (from PI)
        A->>S: Approve CI
        A->>B: Share CI
        B->>S: Accept CI
        A->>S: Create Packing List
        A->>D: Share PL
        A->>S: Create Shipping Instructions
        A->>D: Submit SI
        D->>S: Accept SI
    end

    rect rgb(243, 229, 245)
        Note over A,S: Phase 4: SHIPMENT & LOGISTICS
        D->>D: Issue BL/AWB (external)
        A->>S: Upload BL/AWB to CBOS
        S->>S: Validate BL vs SI/CI/PL
        A->>B: Share BL/AWB
        A->>S: Request COO
        Note right of A: Issuer issues COO externally
        A->>S: Upload COO
        D->>S: Prepare Export Declaration
        A->>S: Approve Export Declaration
        D->>S: Submit to Customs
        S->>S: Customs clears → LEO issued
    end

    rect rgb(224, 247, 250)
        Note over A,S: Phase 5: IMPORT & CLEARANCE
        B->>S: Prepare Import Declaration
        D->>S: Submit to Destination Customs
        S->>S: Customs clears
        B->>S: Confirm delivery
    end

    rect rgb(252, 228, 236)
        Note over A,S: Phase 6: SETTLEMENT & CLOSURE
        S->>C: Payment Ready Signal
        C->>C: Execute payment (external)
        C->>S: Issue Payment Advice
        A->>S: Confirm Payment Advice
        S->>S: Reconcile to CI/Trade
        C->>C: Issue FIRC (external)
        A->>S: Upload FIRC
        S->>S: Map FIRC → close shipping bill (EDPMS)
        S->>S: TRADE CLOSED
    end
```

---

## 4. Individual Document State Machines

### 4.1 Proforma Invoice (PI)

```mermaid
stateDiagram-v2
    [*] --> DRAFT: A creates PI
    DRAFT --> EXPORTER_APPROVED: A approves
    EXPORTER_APPROVED --> SHARED: A shares to B
    SHARED --> COUNTERPARTY_ACCEPTED: B accepts
    SHARED --> RETURNED_FOR_REVISION: B returns
    RETURNED_FOR_REVISION --> DRAFT: A revises
    COUNTERPARTY_ACCEPTED --> CLOSED: Convert to CI
    CLOSED --> [*]
```

### 4.2 Purchase Order / Sales Contract

```mermaid
stateDiagram-v2
    [*] --> DRAFT: B or A+B creates
    DRAFT --> COUNTERPARTY_ACCEPTED: Counterparty accepts
    DRAFT --> RETURNED: Counterparty returns
    RETURNED --> DRAFT: Revise
    COUNTERPARTY_ACCEPTED --> LINKED: System locks & links
    LINKED --> CLOSED: Trade completes
    CLOSED --> [*]
```

### 4.3 Letter of Credit (LC)

```mermaid
stateDiagram-v2
    [*] --> REQUESTED: B requests LC
    REQUESTED --> ISSUED_ADVISED: C issues, advising bank shares
    ISSUED_ADVISED --> ACCEPTED: A accepts
    ISSUED_ADVISED --> AMENDMENT_REQUESTED: A requests amendment
    AMENDMENT_REQUESTED --> ISSUED_ADVISED: C re-issues
    ACCEPTED --> RULES_LINKED: System captures rules
    RULES_LINKED --> DOCS_PRESENTED: A presents docs
    DOCS_PRESENTED --> CLOSED: Settlement completes
    CLOSED --> [*]
```

### 4.4 Commercial Invoice (CI)

```mermaid
stateDiagram-v2
    [*] --> DRAFT: A creates CI
    DRAFT --> APPROVED: A approves
    APPROVED --> SHARED: A shares to B, D
    SHARED --> ACCEPTED: B accepts
    SHARED --> RETURNED: B returns
    RETURNED --> DRAFT: A revises
    ACCEPTED --> SUBMITTED_TO_BANK: Submit to C (optional)
    ACCEPTED --> LINKED: Link to shipment (if no bank review)
    SUBMITTED_TO_BANK --> LINKED: Bank review passes
    LINKED --> PAYMENT_READY: System emits signal
    PAYMENT_READY --> CLOSED: Payment received
    CLOSED --> [*]
```

### 4.5 Packing List (PL)

```mermaid
stateDiagram-v2
    [*] --> DRAFT: A creates PL
    DRAFT --> APPROVED: A approves
    APPROVED --> SHARED: A shares to D, B
    SHARED --> RETURNED: D returns (optional)
    RETURNED --> DRAFT: A revises
    SHARED --> LINKED: System links to BL/AWB
    LINKED --> CLOSED: Delivery milestone
    CLOSED --> [*]
```

### 4.6 Shipping Instructions (SI)

```mermaid
stateDiagram-v2
    [*] --> DRAFT: A creates SI
    DRAFT --> APPROVED: A approves
    APPROVED --> SUBMITTED: A submits to D
    SUBMITTED --> ACCEPTED: D accepts
    SUBMITTED --> RETURNED: D returns
    RETURNED --> DRAFT: A revises
    ACCEPTED --> LINKED: BL/AWB issued
    LINKED --> CLOSED: Close
    CLOSED --> [*]
```

### 4.7 Bill of Lading / Air Waybill (BL/AWB)

```mermaid
stateDiagram-v2
    [*] --> RECEIVED: A uploads BL/AWB
    RECEIVED --> VALIDATED: System validates vs SI/CI/PL
    RECEIVED --> EXCEPTION: Validation fails
    EXCEPTION --> RECEIVED: Re-upload corrected
    VALIDATED --> SHARED: A shares to B, C
    SHARED --> DELIVERED_CLEARED: B confirms delivery
    DELIVERED_CLEARED --> CLOSED: Close
    CLOSED --> [*]
```

### 4.8 Certificate of Origin (COO)

```mermaid
stateDiagram-v2
    [*] --> REQUESTED: A requests COO
    REQUESTED --> ISSUED: Issuer issues (external)
    ISSUED --> RECEIVED: A uploads to CBOS
    RECEIVED --> SHARED: A shares to B, D
    SHARED --> LINKED: System links
    SHARED --> REJECTED: Customs rejects
    REJECTED --> REQUESTED: Re-request
    LINKED --> CLOSED: Clearance accepted
    CLOSED --> [*]
```

### 4.9 Export Declaration / Shipping Bill (India)

```mermaid
stateDiagram-v2
    [*] --> DRAFT: A/D prepares filing
    DRAFT --> APPROVED: A approves pack
    APPROVED --> SUBMITTED: D submits to customs
    SUBMITTED --> CLEARED: Customs clears + LEO
    CLEARED --> LINKED: System links refs
    LINKED --> CLOSED: Trade closure
    CLOSED --> [*]

    note right of LINKED
        EDPMS: Must close within
        9 months of shipment
        Alerts at 6mo and 8mo
    end note
```

### 4.10 Import Declaration / Bill of Entry

```mermaid
stateDiagram-v2
    [*] --> DRAFT: B/D prepares filing
    DRAFT --> SUBMITTED: D submits to customs
    SUBMITTED --> CLEARED: Customs clears
    CLEARED --> LINKED: System links proof
    LINKED --> CLOSED: Post clearance
    CLOSED --> [*]
```

### 4.11 Payment Advice / Bank Credit Confirmation

```mermaid
stateDiagram-v2
    [*] --> RECEIVED: C issues advice
    RECEIVED --> CONFIRMED: A confirms in CBOS
    CONFIRMED --> RECONCILED: System reconciles to CI
    RECONCILED --> CLOSED: Trade marked closed
    CLOSED --> [*]
```

### 4.12 FIRC (Bank-issued)

```mermaid
stateDiagram-v2
    [*] --> RECEIVED: A uploads FIRC from bank
    RECEIVED --> LINKED: System maps to trade/invoice
    LINKED --> CLOSED: Compliance closed
    CLOSED --> [*]

    note right of LINKED
        Triggers EDPMS closure signal
        Closes shipping bill
        FEMA realization complete
    end note
```

---

## 5. Trade Milestones & Document Triggers

Shows how individual document state changes drive trade-level milestone progression.

```mermaid
flowchart LR
    subgraph TRADE["Trade Lifecycle"]
        direction LR
        M1["is_pi_confirmed ✓"] --> M2["is_lc_operative ✓"]
        M2 --> M3["is_shipment_verified ✓"]
        M3 --> M4["is_payment_signalled ✓"]
    end

    PI_ACC["PI → ACCEPTED"] -.->|triggers| M1
    LC_ISS["LC → ISSUED"] -.->|triggers| M2
    BL_VAL["BL/AWB → VALIDATED"] -.->|triggers| M3
    CI_PAY["CI → PAYMENT_READY"] -.->|triggers| M4

    subgraph TRADE_STATUS["Trade Status Driven By Milestones"]
        direction LR
        TS1[INITIATED] -->|PI confirmed| TS2[CONTRACTED]
        TS2 -->|BL validated| TS3[SHIPPED]
        TS3 -->|CI submitted to bank| TS4[DOCS_PRESENTED]
        TS4 -->|Payment received| TS5[SETTLED]
        TS5 -->|FIRC mapped| TS6[CLOSED]
    end

    M1 -.-> TS2
    M3 -.-> TS3
    M4 -.-> TS5

    style TRADE fill:#E8EAF6,stroke:#283593
    style TRADE_STATUS fill:#F1F8E9,stroke:#33691E
```

---

## 6. EDPMS Compliance Timeline (India)

```mermaid
gantt
    title Shipping Bill to FIRC Closure Timeline (FEMA 9-Month Rule)
    dateFormat YYYY-MM-DD
    axisFormat %b %Y

    section Export
    Shipping Bill Filed           :done, sb, 2026-01-15, 1d
    LEO Issued                    :done, leo, after sb, 1d
    Goods Shipped                 :done, ship, after leo, 3d

    section Settlement
    Payment Advice Received       :active, pa, 2026-03-15, 1d
    FIRC Issued by Bank           :active, firc, 2026-03-20, 1d

    section EDPMS Monitoring
    6-Month Alert                 :crit, alert6, 2026-07-15, 1d
    8-Month Alert                 :crit, alert8, 2026-09-15, 1d
    9-Month Deadline (HARD)       :crit, deadline, 2026-10-15, 1d

    section Closure
    FIRC Mapped → SB Closed       :milestone, close, 2026-03-25, 0d
```
