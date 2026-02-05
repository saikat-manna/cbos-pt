# CBOS/PTWL Enhanced PRD v2 — Complete Specification

**CBOS End-to-End Document Journey Playbook (Proforma Invoice -> FIRC) + Developer Spec**

**Audience:** Product + Engineering
**Status:** Enhanced Draft (v2)
**Date:** 2026-02-05
**Compiled from:** Original PRD v1 + Gemini Flash analysis + UCP 600/EDPMS research + architectural gap analysis

---

## Overview

CBOS/PTWL orchestrates the full lifecycle of cross-border trade documents, from initial quote (Proforma Invoice) through final regulatory closure (FIRC). The system is **bank-safe**: CBOS handles documents and emits readiness signals; banks execute payments and issue FIRC.

### Legend (Roles)

| Code | Role |
|------|------|
| A | Exporter |
| B | Importer |
| C | AD Bank |
| D | Forwarder / Carrier / CHA |
| Issuer | COO Issuer |
| System | CBOS/PTWL |

---

## 1. The Trade Object (Aggregate Root)

The `Trade` object is the central orchestrator. All 12 document types attach to a trade, and their individual statuses drive the overall `trade_status`.

### Trade JSON Schema

```json
{
  "trade_id": "UUID",
  "external_ref": "STRING",
  "version": "INTEGER",
  "participants": {
    "exporter_id": "ORG_UUID",
    "importer_id": "ORG_UUID",
    "ad_bank_id": "ORG_UUID (optional)",
    "forwarder_id": "ORG_UUID (optional)"
  },
  "financials": {
    "currency": "ISO_4217",
    "total_value": "DECIMAL",
    "incoterms": "ENUM (FOB, CIF, EXW, DDP, etc.)",
    "payment_terms": "STRING"
  },
  "regulatory_context": {
    "origin_country": "ISO_3166",
    "destination_country": "ISO_3166",
    "regime": "INDIA_FEMA | GENERIC"
  },
  "status": "INITIATED | CONTRACTED | SHIPPED | DOCS_PRESENTED | SETTLED | CLOSED",
  "milestones": {
    "is_pi_confirmed": false,
    "is_lc_operative": false,
    "is_shipment_verified": false,
    "is_payment_signalled": false
  },
  "document_registry": [
    { "doc_type": "PI", "doc_id": "UUID", "status": "STRING" }
  ],
  "metadata": {
    "created_at": "ISO8601",
    "updated_at": "ISO8601",
    "created_by": "USER_UUID"
  }
}
```

### Trade Lifecycle

```
INITIATED -> CONTRACTED -> SHIPPED -> DOCS_PRESENTED -> SETTLED -> CLOSED
```

Milestones flip to `true` as child documents reach key states, driving the trade forward automatically:

| Milestone | Triggered By |
|-----------|-------------|
| `is_pi_confirmed` | PI reaches ACCEPTED state |
| `is_lc_operative` | LC reaches ISSUED state |
| `is_shipment_verified` | BL/AWB reaches VALIDATED state |
| `is_payment_signalled` | CI reaches PAYMENT_READY state |

### Trade API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/trades` | Create a new trade |
| GET | `/api/v1/trades/{tradeId}` | Get trade details |
| GET | `/api/v1/trades` | List trades (with filters) |
| GET | `/api/v1/trades/{tradeId}/documents` | List all documents in a trade |
| GET | `/api/v1/trades/{tradeId}/timeline` | Get trade event timeline |
| GET | `/api/v1/trades/{tradeId}/milestones` | Get milestone status |

---

## 2. Global State Machine (Reusable)

### Core Flow

```
Draft -> Exporter Approved -> Shared -> Counterparty Accepted -> Submitted
-> In Review -> Issued/Approved -> Linked -> Payment Ready (Signal) -> Closed -> Archived
```

### Exception States

```
Returned for Revision | Rejected | Expired | Cancelled
```

Not all documents use every state. Each document type inherits a subset of this global FSM.

---

## 3. Global RBAC Matrix

### Default Permissions (Role x State)

| Role | Draft | Shared | Accepted | In Review | Issued | Closed |
|------|-------|--------|----------|-----------|--------|--------|
| **Exporter (A)** | CRUD | Read | Read | Read | Read | Read |
| **Importer (B)** | -- | Read/Reject | Read/Accept | Read | Read | Read |
| **AD Bank (C)** | -- | -- | -- | Read/Review | Read/Approve | Read |
| **Forwarder (D)** | -- | -- | -- | Read | Read | Read |
| **System** | -- | -- | -- | Auto-transition | Auto-transition | Archive |

### Per-Document Overrides

| Document | Creator Override | Additional Permissions |
|----------|----------------|----------------------|
| PO/Contract | B creates (or A+B jointly) | Both parties can edit in Draft |
| BL/AWB | D creates (issues externally) | A uploads/receives |
| COO | Issuer creates externally | A uploads to CBOS |
| Export Declaration | D (CHA) prepares | A approves pack |
| Import Declaration | B/D prepares | -- |
| Payment Advice | C (bank) issues | A captures/confirms |
| FIRC | C (bank) issues | A captures/uploads |

---

## 4. The 12 Document Flows

### Shared Object Schema (All Documents)

Every document object shares this base:

```json
{
  "id": "UUID",
  "trade_id": "UUID",
  "type": "STRING (object.type)",
  "version": "INTEGER",
  "state": "ENUM",
  "created_at": "ISO8601",
  "updated_at": "ISO8601",
  "created_by": "USER_UUID",
  "attachments": ["FILE_UUID"]
}
```

---

### Document 1: Proforma Invoice (PI)

**Purpose:** Preliminary quote/invoice for buyer confirmation and (optionally) LC initiation.
**Object type:** `proforma_invoice`
**Primary actors:** A, B, (C optional), System

#### Flow

| Step | Role | Action | Endpoint | State Transition | Event | Validations | Notifications |
|------|------|--------|----------|-----------------|-------|-------------|---------------|
| 1 | A | Create PI | `POST /api/v1/trades/{tradeId}/pi` | -- -> DRAFT | PI_CREATED | Hard: mandatory fields present, currency in ISO 4217 | -- |
| 2 | A | Approve PI | `POST .../pi/{id}/approve` | DRAFT -> EXPORTER_APPROVED | PI_APPROVED | Hard: totals.total = sum(line_items[].amount), validity_date not in past | -- |
| 3 | A | Share to B | `POST .../pi/{id}/share` | EXPORTER_APPROVED -> SHARED | PI_SHARED | -- | B: email + in-app |
| 4 | B | Accept or Return | `POST .../pi/{id}/accept` or `POST .../pi/{id}/return` | SHARED -> COUNTERPARTY_ACCEPTED / RETURNED_FOR_REVISION | PI_ACCEPTED / PI_RETURNED | Soft: unit price vs market average | A: email + in-app |
| 5 | System | Convert to CI | `POST .../pi/{id}/convert` | COUNTERPARTY_ACCEPTED -> CLOSED | PI_CONVERTED_TO_CI | Hard: state must be ACCEPTED | A: in-app |

#### Required JSON Fields (MVP)

```json
{
  "seller": { "name": "STRING", "country": "ISO_3166", "gst_or_tax_id": "STRING (optional)" },
  "buyer": { "name": "STRING", "country": "ISO_3166" },
  "currency": "ISO_4217",
  "incoterms": "ENUM",
  "validity_date": "DATE",
  "line_items": [
    { "description": "STRING", "qty": "DECIMAL", "unit_price": "DECIMAL", "amount": "DECIMAL" }
  ],
  "totals": { "subtotal": "DECIMAL", "total": "DECIMAL" },
  "shipment": {
    "estimated_date": "DATE",
    "origin_port": "STRING (optional)",
    "destination_port": "STRING (optional)"
  }
}
```

#### Closure Definition

Closed when converted into Commercial Invoice (CI) or trade is cancelled.

#### Integrations

- Optional: LC request reference captured if buyer indicates LC intent
- PDF render service (template id + locale)

---

### Document 2: Purchase Order (PO) / Sales Contract

**Purpose:** Master trade terms used to validate downstream documents.
**Object type:** `po_or_contract`
**Primary actors:** B (PO) / A+B (Contract), System

#### Flow

| Step | Role | Action | Endpoint | State Transition | Event | Validations | Notifications |
|------|------|--------|----------|-----------------|-------|-------------|---------------|
| 1 | B or A+B | Create PO/Contract | `POST /api/v1/trades/{tradeId}/po` | -- -> DRAFT | MASTER_TERMS_CREATED | Hard: incoterms/payment_terms in enums, qty > 0, price >= 0 | -- |
| 2 | Counterparty | Accept or Return | `POST .../po/{id}/accept` or `/return` | DRAFT -> COUNTERPARTY_ACCEPTED / RETURNED | MASTER_TERMS_ACCEPTED / MASTER_TERMS_RETURNED | Soft: items align with PI/CI if linked | Counterparty: email + in-app |
| 3 | System | Lock & Link | automatic on accept | COUNTERPARTY_ACCEPTED -> LINKED | MASTER_TERMS_LINKED | -- | -- |
| 4 | System | Close | automatic at trade completion | LINKED -> CLOSED | MASTER_TERMS_CLOSED | -- | -- |

#### Required JSON Fields (MVP)

```json
{
  "party_a": { "name": "STRING" },
  "party_b": { "name": "STRING" },
  "terms": {
    "incoterms": "ENUM",
    "payment_terms": "STRING",
    "delivery_terms": "STRING"
  },
  "items": [
    { "description": "STRING", "qty": "DECIMAL", "unit_price": "DECIMAL" }
  ],
  "dates": {
    "contract_date": "DATE",
    "ship_by_date": "DATE (optional)"
  }
}
```

#### Closure Definition

Closed when trade completes (delivery + settlement) or cancelled.

#### Integrations

- Optional: e-sign/acceptance capture
- Optional: attach signed PDF as evidence

---

### Document 3: Letter of Credit (LC) -- Optional

**Purpose:** Bank instrument requiring compliant doc presentation for settlement.
**Object type:** `letter_of_credit`
**Primary actors:** B, C/E (banks), A, System

#### Flow

| Step | Role | Action | Endpoint | State Transition | Event | Validations | Notifications |
|------|------|--------|----------|-----------------|-------|-------------|---------------|
| 1 | B | Request LC | `POST /api/v1/trades/{tradeId}/lc` | -- -> REQUESTED | LC_REQUESTED | Hard: amount > 0, currency present | C: webhook |
| 2 | C (Issuing Bank) | Issue LC; advising bank shares to A | `POST .../lc/{id}/issue` | REQUESTED -> ISSUED/ADVISED | LC_ADVISED | Hard: SWIFT MT700 format, expiry_date >= issue_date | A: email (critical) |
| 3 | A | Accept or request amendment | `POST .../lc/{id}/accept` or `/amend` | ISSUED -> ACCEPTED / AMENDMENT_REQUESTED | LC_ACCEPTED / LC_AMENDMENT_REQUESTED | Hard: amount/currency match master terms within tolerance | C: webhook |
| 4 | System | Capture LC conditions as structured rules | automatic | ACCEPTED -> RULES_LINKED | LC_RULES_LINKED | -- | -- |
| 5 | A | Present docs through bank | `POST .../lc/{id}/present-docs` | RULES_LINKED -> DOCS_PRESENTED | LC_DOCS_PRESENTED | Hard: all required_docs present and valid | C: webhook |
| 6 | System | Settlement completes | automatic | DOCS_PRESENTED -> CLOSED | LC_CLOSED | -- | A, B: email |

#### Required JSON Fields (MVP)

```json
{
  "lc_number": "STRING",
  "lc_issue_date": "DATE",
  "lc_expiry_date": "DATE",
  "lc_amount": "DECIMAL",
  "lc_currency": "ISO_4217",
  "required_docs": ["CI", "BL/AWB", "PL", "COO"],
  "latest_shipment_date": "DATE",
  "tolerances": {
    "amount_percent": 5,
    "quantity_percent": 5
  }
}
```

#### LC Rules Engine

The LC is the most complex document. Its `required_docs[]` field drives a validation engine:

**UCP 600 Compliance (39 articles):**
- Articles 14-22 govern document examination rules
- ISBP 821 (updated July 2023) provides interpretation guidance
- Tolerance matching: amount +/- configurable % (default 5% per UCP 600 Art. 30)
- Quantity tolerance: +/- 5% unless LC specifies exact

**Rules Engine Design:**
- `required_docs[]` creates a checklist -- each doc type in the trade is checked against LC conditions
- Discrepancy handling: system flags discrepancies, bank reviews, exporter can cure or request waiver from importer
- Platform references: ClearEye ClearTrade and Traydstream use 300K+ rule variations for automated LC examination

**Discrepancy Workflow:**
```
Doc Presented -> Rules Engine Check -> PASS: proceed to settlement
                                    -> FAIL: flag discrepancies
                                       -> Exporter cures and re-presents
                                       -> OR Importer waives discrepancy
                                       -> OR Bank rejects
```

#### Closure Definition

Closed when settlement is completed and bank confirms closure.

#### Integrations

- Bank document upload portal integration (phase later)
- Rules engine reads LC.required_docs to enforce checks

---

### Document 4: Commercial Invoice (CI)

**Purpose:** Primary value document for customs/bank scrutiny and settlement reference.
**Object type:** `commercial_invoice`
**Primary actors:** A, B, C (optional), D (optional), System

#### Flow

| Step | Role | Action | Endpoint | State Transition | Event | Validations | Notifications |
|------|------|--------|----------|-----------------|-------|-------------|---------------|
| 1 | A | Create CI | `POST /api/v1/trades/{tradeId}/ci` | -- -> DRAFT | CI_CREATED | Hard: mandatory fields, totals = sum(line_items) | -- |
| 2 | A | Approve | `POST .../ci/{id}/approve` | DRAFT -> APPROVED | CI_APPROVED | Hard: buyer name matches master terms | -- |
| 3 | A | Share to B, D, bank | `POST .../ci/{id}/share` | APPROVED -> SHARED | CI_SHARED | -- | B, D: email + in-app |
| 4 | B | Accept or Return | `POST .../ci/{id}/accept` or `/return` | SHARED -> ACCEPTED / RETURNED | CI_ACCEPTED / CI_RETURNED | -- | A: email + in-app |
| 5 | C | Bank review (optional) | `POST .../ci/{id}/submit-to-bank` | ACCEPTED -> SUBMITTED_TO_BANK | CI_SUBMITTED_TO_BANK | Hard: if LC present, amount/date per LC rules | C: webhook |
| 6 | System | Link to shipment + regulatory refs | automatic | SUBMITTED -> LINKED | CI_LINKED_TO_SHIPMENT | -- | -- |
| 7 | System | Emit payment readiness signal | automatic | LINKED -> PAYMENT_READY | CI_PAYMENT_READY_SIGNAL | -- | A, C: in-app + webhook |
| 8 | System | Payment received | automatic | PAYMENT_READY -> CLOSED | CI_CLOSED | -- | A: email |

#### Required JSON Fields (MVP)

```json
{
  "invoice_number": "STRING",
  "invoice_date": "DATE",
  "seller": { "name": "STRING", "country": "ISO_3166" },
  "buyer": { "name": "STRING", "country": "ISO_3166" },
  "currency": "ISO_4217",
  "incoterms": "ENUM",
  "line_items": [
    { "description": "STRING", "qty": "DECIMAL", "unit_price": "DECIMAL", "amount": "DECIMAL" }
  ],
  "totals": { "subtotal": "DECIMAL", "total": "DECIMAL" },
  "hs_codes": ["STRING (optional in MVP)"],
  "references": {
    "po_id": "UUID (optional)",
    "lc_number": "STRING (optional)"
  }
}
```

#### Cross-Document Validations

- Hard: `CI.total_value` cannot exceed `PI.total_value` by > 10%
- Hard: if LC present, amount/date constraints per LC rules (phase-wise strictness)
- Soft: HS codes format check

#### Closure Definition

Closed when payment advice/credit confirmation is reconciled to trade.

#### Integrations

- PDF render; doc-pack export (zip)
- Optional: bank API for doc submission (later)

---

### Document 5: Packing List (PL)

**Purpose:** Packaging details used for logistics and customs.
**Object type:** `packing_list`
**Primary actors:** A, D, B, System

#### Flow

| Step | Role | Action | Endpoint | State Transition | Event | Validations | Notifications |
|------|------|--------|----------|-----------------|-------|-------------|---------------|
| 1 | A | Create PL | `POST /api/v1/trades/{tradeId}/pl` | -- -> DRAFT | PL_CREATED | Hard: weights > 0, package counts > 0 | -- |
| 2 | A | Approve | `POST .../pl/{id}/approve` | DRAFT -> APPROVED | PL_APPROVED | -- | -- |
| 3 | A | Share to D and B | `POST .../pl/{id}/share` | APPROVED -> SHARED | PL_SHARED | -- | D, B: email + in-app |
| 4 | D | Review/return (optional) | `POST .../pl/{id}/return` | SHARED -> RETURNED | PL_RETURNED | -- | A: email + in-app |
| 5 | System | Link to BL/AWB | automatic | SHARED -> LINKED | PL_LINKED_TO_SHIPMENT | Soft: sum qty aligns with CI line items | -- |
| 6 | System | Delivery milestone | automatic | LINKED -> CLOSED | PL_CLOSED | -- | -- |

#### Required JSON Fields (MVP)

```json
{
  "packages": [
    {
      "package_no": "INTEGER",
      "package_type": "STRING",
      "gross_weight": "DECIMAL",
      "net_weight": "DECIMAL (optional)",
      "dimensions": "STRING (optional)"
    }
  ],
  "marks_and_numbers": "STRING (optional)",
  "references": { "ci_id": "UUID" }
}
```

#### Closure Definition

Closed when delivery/inspection milestone is completed and linked.

#### Integrations

- Optional: forwarder portal share link
- Link to BL/AWB id once issued

---

### Document 6: Shipping Instructions (SI)

**Purpose:** Instructions used to generate BL/AWB correctly.
**Object type:** `shipping_instructions`
**Primary actors:** A, D, System

#### Flow

| Step | Role | Action | Endpoint | State Transition | Event | Validations | Notifications |
|------|------|--------|----------|-----------------|-------|-------------|---------------|
| 1 | A | Create SI | `POST /api/v1/trades/{tradeId}/si` | -- -> DRAFT | SI_CREATED | Hard: ports are supported codes, freight_terms enum | -- |
| 2 | A | Approve | `POST .../si/{id}/approve` | DRAFT -> APPROVED | SI_APPROVED | -- | -- |
| 3 | A | Submit to D | `POST .../si/{id}/submit` | APPROVED -> SUBMITTED | SI_SUBMITTED | -- | D: email/secure-link |
| 4 | D | Accept or Return | `POST .../si/{id}/accept` or `/return` | SUBMITTED -> ACCEPTED / RETURNED | SI_ACCEPTED / SI_RETURNED | Soft: consignee aligns with master terms/LC | A: email + in-app |
| 5 | System | BL/AWB issued, link SI | automatic | ACCEPTED -> LINKED | SI_LINKED_TO_BL_AWB | -- | -- |
| 6 | System | Close | automatic | LINKED -> CLOSED | SI_CLOSED | -- | -- |

#### Required JSON Fields (MVP)

```json
{
  "consignee": { "name": "STRING" },
  "notify_party": { "name": "STRING (optional)" },
  "origin_port": "PORT_CODE",
  "destination_port": "PORT_CODE",
  "freight_terms": "ENUM (prepaid | collect)",
  "commodity_description": "STRING",
  "references": {
    "ci_id": "UUID",
    "pl_id": "UUID (optional)"
  }
}
```

#### Closure Definition

Closed when BL/AWB issued and validated against SI.

#### Integrations

- Email/secure-link dispatch to forwarder
- Later: carrier API connector

---

### Document 7: Bill of Lading (BL) / Air Waybill (AWB)

**Purpose:** Transport document proving shipment; used for import clearance and bank scrutiny.
**Object type:** `bl_or_awb`
**Primary actors:** D issues; A captures; B & C consume; System validates

#### Flow

| Step | Role | Action | Endpoint | State Transition | Event | Validations | Notifications |
|------|------|--------|----------|-----------------|-------|-------------|---------------|
| 1 | D | Issue BL/AWB (external) | -- | -- | -- | -- | -- |
| 2 | A | Upload/pull into CBOS | `POST /api/v1/trades/{tradeId}/bl` | -- -> RECEIVED | BL_AWB_RECEIVED | Hard: number format present | -- |
| 3 | System | Validate vs SI/CI/PL | automatic | RECEIVED -> VALIDATED | BL_AWB_VALIDATED | Hard: ports match SI. Soft: gross weight variance < 5% vs PL, departure within LC latest shipment date | -- |
| 4 | A | Share to B and C | `POST .../bl/{id}/share` | VALIDATED -> SHARED | BL_AWB_SHARED | -- | B, C: email + webhook |
| 5 | B | Confirm delivery/clearance | `POST .../bl/{id}/confirm-delivery` | SHARED -> DELIVERED_CLEARED | BL_AWB_DELIVERED_CLEARED | -- | A: in-app |
| 6 | System | Close | automatic | DELIVERED -> CLOSED | BL_AWB_CLOSED | -- | -- |

#### Required JSON Fields (MVP)

```json
{
  "transport_number": "STRING",
  "transport_type": "ENUM (BL | AWB)",
  "shipment": {
    "departure_date": "DATE",
    "arrival_eta": "DATE (optional)"
  },
  "origin_port": "PORT_CODE",
  "destination_port": "PORT_CODE",
  "consignee": "STRING",
  "notify_party": "STRING (optional)",
  "references": {
    "si_id": "UUID",
    "ci_id": "UUID",
    "pl_id": "UUID"
  }
}
```

#### Closure Definition

Closed after delivery/clearance and any bank review completes.

#### Integrations

- Carrier/forwarder integration (later)
- OCR/extraction optional in later phase

---

### Document 8: Certificate of Origin (COO) -- Lane Dependent

**Purpose:** Origin certificate for customs/tariff benefits.
**Object type:** `certificate_of_origin`
**Primary actors:** Issuer issues; A captures; B/D use; System links

#### Flow

| Step | Role | Action | Endpoint | State Transition | Event | Validations | Notifications |
|------|------|--------|----------|-----------------|-------|-------------|---------------|
| 1 | A | Request COO | `POST /api/v1/trades/{tradeId}/coo` | -- -> REQUESTED | COO_REQUESTED | -- | Issuer: email |
| 2 | Issuer | Issue COO (external) | -- | -- | -- | -- | -- |
| 3 | A | Upload to CBOS | `POST .../coo/{id}/receive` | REQUESTED -> RECEIVED | COO_RECEIVED | Hard: issue_date present, origin/destination countries present | -- |
| 4 | A | Share to B and D | `POST .../coo/{id}/share` | RECEIVED -> SHARED | COO_SHARED | Soft: goods description aligns with CI (fuzzy match in MVP) | B, D: email + in-app |
| 5 | System | Link to trade | automatic | SHARED -> LINKED | COO_LINKED | -- | -- |
| 6 | System | Close | automatic | LINKED -> CLOSED | COO_CLOSED | -- | -- |

#### Required JSON Fields (MVP)

```json
{
  "coo_number": "STRING",
  "coo_issue_date": "DATE",
  "coo_issuer_name": "STRING",
  "origin_country": "ISO_3166",
  "destination_country": "ISO_3166",
  "hs_codes": ["STRING (optional)"],
  "goods_description": "STRING",
  "references": {
    "ci_id": "UUID",
    "bl_awb_id": "UUID"
  }
}
```

#### Closure Definition

Closed once used/accepted for clearance; archived with issuer proof.

#### Integrations

- Issuer portal/manual upload in MVP
- Later: issuer API integration

---

### Document 9: Export Declaration / Shipping Bill (India)

**Purpose:** Customs export filing producing Shipping Bill + LEO references.
**Object type:** `export_declaration`
**Primary actors:** D (CHA) submits; A approves; System records refs

#### Flow

| Step | Role | Action | Endpoint | State Transition | Event | Validations | Notifications |
|------|------|--------|----------|-----------------|-------|-------------|---------------|
| 1 | A/D (CHA) | Prepare filing from CI/PL | `POST /api/v1/trades/{tradeId}/export-decl` | -- -> DRAFT | EX_DECL_CREATED | Hard: port_code present | -- |
| 2 | A | Approve pack | `POST .../export-decl/{id}/approve` | DRAFT -> APPROVED | EX_DECL_APPROVED | Soft: CI totals vs declaration totals | -- |
| 3 | D | Submit via authorized channel | `POST .../export-decl/{id}/submit` | APPROVED -> SUBMITTED | EX_DECL_SUBMITTED | Hard: IEC format check (if captured) | -- |
| 4 | System | Customs clears; LEO refs returned | `POST .../export-decl/{id}/clear` | SUBMITTED -> CLEARED | EX_DECL_CLEARED | -- | A: email + in-app |
| 5 | System | Link refs to trade/shipment | automatic | CLEARED -> LINKED | EX_DECL_LINKED | -- | -- |
| 6 | System | Close at trade closure | automatic | LINKED -> CLOSED | EX_DECL_CLOSED | -- | -- |

#### Required JSON Fields (MVP)

```json
{
  "shipping_bill_number": "STRING (once issued)",
  "shipping_bill_date": "DATE",
  "leo_date": "DATE (once issued)",
  "iec": "STRING (optional if collected)",
  "port_code": "PORT_CODE",
  "references": {
    "ci_id": "UUID",
    "pl_id": "UUID",
    "bl_awb_id": "UUID (later)"
  }
}
```

#### India FEMA / EDPMS Compliance

> **Critical:** This document feeds directly into RBI's **EDPMS** (Export Data Processing and Monitoring System).

- EDPMS is a centralized online platform jointly operated by RBI, Customs, and AD banks to monitor all export shipments and the realization of export proceeds
- EDPMS tracks each export's shipping bill and matches it with the corresponding FIRC
- **9-month rule:** All export shipping bills must be closed within 9 months of shipment per FEMA guidelines
- Failure to comply results in **caution listing** and restrictions on future exports
- System must emit alerts at **6-month** and **8-month** marks for unclosed shipping bills

#### Closure Definition

Closed once LEO/clearance refs are captured and linked; archived for audits.

#### Integrations

- No direct ICEGATE API in MVP; store CHA-provided refs
- Later: integration via authorized intermediaries

---

### Document 10: Import Declaration / Bill of Entry (Destination)

**Purpose:** Destination customs filing for clearance/duty payment.
**Object type:** `import_declaration`
**Primary actors:** B/D submit; System stores proof

#### Flow

| Step | Role | Action | Endpoint | State Transition | Event | Validations | Notifications |
|------|------|--------|----------|-----------------|-------|-------------|---------------|
| 1 | B/D | Prepare filing using BL/AWB, CI, COO | `POST /api/v1/trades/{tradeId}/import-decl` | -- -> DRAFT | IM_DECL_CREATED | -- | -- |
| 2 | D | Submit via authorized channel | `POST .../import-decl/{id}/submit` | DRAFT -> SUBMITTED | IM_DECL_SUBMITTED | -- | -- |
| 3 | System | Customs clears; clearance proof obtained | `POST .../import-decl/{id}/clear` | SUBMITTED -> CLEARED | IM_DECL_CLEARED | Hard: clearance proof attachment required (PDF/image) | B: email + in-app |
| 4 | System | Link proof & update milestone | automatic | CLEARED -> LINKED | IM_DECL_LINKED | Soft: importer name vs master terms | -- |
| 5 | System | Close | automatic | LINKED -> CLOSED | IM_DECL_CLOSED | -- | -- |

#### Required JSON Fields (MVP)

```json
{
  "boe_number": "STRING (if available)",
  "boe_date": "DATE",
  "clearance_date": "DATE (if available)",
  "references": {
    "ci_id": "UUID",
    "bl_awb_id": "UUID",
    "coo_id": "UUID"
  }
}
```

#### Closure Definition

Closed when clearance is confirmed and linked to trade.

#### Integrations

- Country-specific customs integration later; MVP uses uploads + refs

---

### Document 11: Payment Advice / Bank Credit Confirmation

**Purpose:** Bank-issued confirmation of settlement/credit used to close the trade financially.
**Object type:** `payment_advice`
**Primary actors:** C issues; A captures; System reconciles

#### Flow

| Step | Role | Action | Endpoint | State Transition | Event | Validations | Notifications |
|------|------|--------|----------|-----------------|-------|-------------|---------------|
| 1 | C (Bank) | Execute payment | -- (bank internal, external event) | -- | -- | -- | -- |
| 2 | C | Issue advice/credit confirmation | `POST /api/v1/trades/{tradeId}/payment-advice` | -- -> RECEIVED | PAYMENT_ADVICE_RECEIVED | Hard: amount > 0, currency present, bank_ref unique for trade | A: email (critical) |
| 3 | A | Upload/confirm in CBOS | `POST .../payment-advice/{id}/confirm` | RECEIVED -> CONFIRMED | -- | -- | -- |
| 4 | System | Reconcile to CI/trade | automatic | CONFIRMED -> RECONCILED | PAYMENT_ADVICE_RECONCILED | Hard: amount/currency match CI within tolerance | -- |
| 5 | System | Mark trade closed | automatic | RECONCILED -> CLOSED | TRADE_CLOSED | -- | A, B: email |

#### Required JSON Fields (MVP)

```json
{
  "bank_ref": "STRING",
  "value_date": "DATE",
  "amount": "DECIMAL",
  "currency": "ISO_4217",
  "payer_name": "STRING (optional)",
  "beneficiary_name": "STRING",
  "references": {
    "ci_id": "UUID",
    "trade_id": "UUID"
  }
}
```

#### Closure Definition

Closed when reconciled and used to close trade record.

#### Integrations

- Later: bank statement feed integration (AA/statement APIs where possible)

---

### Document 12: FIRC (Bank-issued)

**Purpose:** Bank-issued certificate for inward remittance used for FEMA/export realization closure.
**Object type:** `firc`
**Primary actors:** C issues; A captures; System maps

#### Flow

| Step | Role | Action | Endpoint | State Transition | Event | Validations | Notifications |
|------|------|--------|----------|-----------------|-------|-------------|---------------|
| 1 | C (Bank) | Issue FIRC | -- (bank internal) | -- | -- | -- | -- |
| 2 | A | Receive and upload to CBOS | `POST /api/v1/trades/{tradeId}/firc` | -- -> RECEIVED | FIRC_RECEIVED | Hard: firc_issue_date present, amount/currency match payment advice/CI within tolerance | -- |
| 3 | System | Map to trade/invoice, mark compliance closure | automatic | RECEIVED -> LINKED | FIRC_LINKED | Hard: purpose code (e.g., P0103) must match Export Declaration | A: email + in-app |
| 4 | System | Close compliance | automatic | LINKED -> CLOSED | COMPLIANCE_CLOSED | -- | A: email |

#### Required JSON Fields (MVP)

```json
{
  "firc_number": "STRING",
  "firc_issue_date": "DATE",
  "amount": "DECIMAL",
  "currency": "ISO_4217",
  "remitter_details": "STRING (optional)",
  "references": {
    "trade_id": "UUID",
    "ci_id": "UUID",
    "payment_advice_id": "UUID"
  }
}
```

#### India FEMA / EDPMS Compliance

- FIRC must close the corresponding shipping bill in **EDPMS within 9 months** of shipment
- System triggers **EDPMS closure signal** when FIRC is linked to trade
- If FIRC amount < shipping bill amount, system flags for manual reconciliation
- Exporters must repatriate 100% of export earnings within the stipulated period

#### Closure Definition

Closed when FIRC is mapped to trade and compliance milestone is marked complete.

#### Integrations

- No issuance by platform; store bank document + structured fields

---

## 5. Validation Engine

### Hard vs Soft vs Configurable

| Type | Behavior | HTTP Response | Examples |
|------|----------|---------------|---------|
| **Hard (Blocking)** | Blocks state transition, returns error | 400 Bad Request | JSON schema violations, identity mismatch, missing mandatory fields |
| **Soft (Warning)** | Logged as warning, allows proceed | 200 with `warnings[]` array | Date anomalies, fuzzy description mismatch, weight variance 1-5% |
| **Configurable** | Admin toggles hard/soft per rule per org | Depends on setting | LC tolerance %, weight variance threshold, HS code matching strictness |

### Cross-Document Consistency Rules

| Rule | Type | Description |
|------|------|-------------|
| CI total <= PI total + 10% | Hard | Commercial Invoice cannot exceed Proforma Invoice by more than 10% |
| PL weights ~ BL gross weight | Soft | Packing List total weight should be within 5% of BL gross weight |
| BL ports = SI ports | Hard | Bill of Lading ports must exactly match Shipping Instructions |
| COO goods ~ CI goods | Soft | Certificate of Origin goods description should align with CI (fuzzy match) |
| FIRC amount ~ Payment Advice | Hard | FIRC amount must match Payment Advice within tolerance |
| Shipping bill closure < 9 months | Hard | EDPMS compliance: shipping bill must be closed within 9 months |
| LC amount tolerance | Configurable | Default +/- 5% per UCP 600 Art. 30, admin-adjustable |
| Buyer name on CI = PO buyer | Hard | Commercial Invoice buyer must match Purchase Order buyer |
| SI consignee ~ LC beneficiary | Soft | Shipping Instructions consignee should align with LC terms |

### Validation Error Response Format

```json
{
  "status": 400,
  "code": "ERR_VALIDATION",
  "errors": [
    {
      "field": "totals.total",
      "rule": "CI_TOTAL_EXCEEDS_PI",
      "type": "hard",
      "message": "CI total (52,000 USD) exceeds PI total (45,000 USD) by 15.6%. Maximum allowed: 10%."
    }
  ],
  "warnings": [
    {
      "field": "line_items[0].description",
      "rule": "DESCRIPTION_FUZZY_MISMATCH",
      "type": "soft",
      "message": "CI description 'Widget Alpha' does not exactly match PI description 'Widget A'."
    }
  ]
}
```

---

## 6. Event Bus & Notification Spec

### Event Schema

```json
{
  "event_id": "UUID",
  "event_type": "DOCUMENT_STATE_CHANGED | TRADE_MILESTONE_REACHED | VALIDATION_WARNING | COMPLIANCE_ALERT",
  "timestamp": "ISO8601",
  "actor": {
    "user_id": "UUID",
    "org_id": "UUID",
    "role": "EXPORTER | IMPORTER | AD_BANK | FORWARDER | SYSTEM"
  },
  "trade_id": "UUID",
  "document": {
    "type": "STRING",
    "id": "UUID",
    "version": "INTEGER"
  },
  "transition": {
    "from": "STRING (previous state)",
    "to": "STRING (new state)"
  },
  "payload": {}
}
```

### Architecture

- **Internal:** Message broker (Redis Pub/Sub or Kafka) for real-time UI updates and inter-service communication
- **Pattern:** Mediator topology -- central orchestrator processes multi-step events (e.g., "CI accepted" triggers: update trade milestone + check LC conditions + notify bank)
- **Persistence:** All events saved to immutable `audit_logs` table (append-only)

### Notification Channels per Role

| Role | In-App | Email | Webhook | SMS |
|------|--------|-------|---------|-----|
| Exporter (A) | Yes | Yes | Optional | -- |
| Importer (B) | Yes | Yes | Optional | -- |
| AD Bank (C) | -- | -- | **Yes (primary)** | -- |
| Forwarder (D) | Yes | Yes | -- | Optional |

### Webhook Spec (for Bank Integration)

```json
POST {bank_webhook_url}
Content-Type: application/json
X-CBOS-Signature: HMAC-SHA256

{
  "event_type": "CI_PAYMENT_READY_SIGNAL",
  "trade_id": "UUID",
  "document": { "type": "CI", "id": "UUID" },
  "payload": {
    "amount": 50000,
    "currency": "USD",
    "beneficiary": "Exporter Corp"
  }
}
```

---

## 7. Concurrency & Versioning

### Optimistic Locking Strategy

1. Every Document and Trade object has a `version` (integer)
2. All `PUT/POST` mutation requests must include `If-Match: {version}` header
3. If DB version > request version, return `409 Conflict`
4. Client must re-fetch latest version and retry

### Version History

- Every update creates a new record in `document_versions` table
- Main `documents` table points to `current_version_id`
- Full history preserved for audit trail

### Idempotency

- API gateway checks `X-Idempotency-Key` header on all POST requests
- Duplicate requests return cached response (same status, same body)
- Idempotency keys expire after 24 hours

---

## 8. File & Attachment Model

### File Metadata Schema

```json
{
  "file_id": "UUID",
  "document_id": "UUID",
  "original_name": "invoice_v1.pdf",
  "mime_type": "application/pdf",
  "size_bytes": 2048576,
  "checksum": "SHA-256",
  "storage_path": "s3://cbos-docs/{trade_id}/{doc_type}/{file_id}",
  "access_url": "SIGNED_URL (expires 15min)",
  "uploaded_by": "USER_UUID",
  "uploaded_at": "ISO8601"
}
```

### Constraints

- **Max file size:** 25 MB per file
- **Allowed formats:** PDF, XML, JSON, PNG, JPG (for clearance proofs)
- **Storage:** S3-compatible object storage with AES-256 encryption at rest
- **Access:** Signed URLs with 15-minute expiry; regenerated on each request
- **Retention:** Minimum 7 years for compliance (configurable per regime)

### File API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/trades/{tradeId}/documents/{docId}/files` | Upload attachment |
| GET | `/api/v1/trades/{tradeId}/documents/{docId}/files` | List attachments |
| GET | `/api/v1/trades/{tradeId}/documents/{docId}/files/{fileId}` | Get signed download URL |
| DELETE | `/api/v1/trades/{tradeId}/documents/{docId}/files/{fileId}` | Remove attachment (Draft state only) |

---

## 9. Error Handling & Recovery

### Error Codes

| Code | Scenario | HTTP Status | Resolution |
|------|----------|-------------|------------|
| `ERR_VAL_001` | Hard validation failure | 400 | Return field-level error array; client fixes and retries |
| `ERR_CONFLICT_002` | Optimistic lock conflict | 409 | Client re-fetches latest version and retries |
| `ERR_STUCK_003` | Document pending > 48 hours | -- | Auto "nudge" notification to responsible role |
| `ERR_IDEM_004` | Duplicate request | 200 | Return cached response via idempotency key |
| `ERR_AUTH_005` | RBAC violation | 403 | Logged in security audit; client shown access denied |
| `ERR_EDPMS_006` | Shipping bill approaching 9-month limit | -- | Alert at 6-month and 8-month marks |
| `ERR_EXT_007` | External system unavailable | 502/503 | Queue for retry with exponential backoff |
| `ERR_STATE_008` | Invalid state transition attempted | 422 | Return allowed transitions for current state |

### Stuck State Recovery

- System monitors all documents for age in non-terminal states
- At 48h: sends "nudge" notification to the role responsible for the next action
- At 7 days: escalates to trade administrator
- Admin can force-transition with reason_code (logged in audit)

### Retry & Idempotency

- All webhook deliveries: retry 3 times with exponential backoff (1s, 5s, 30s)
- All API mutations: `X-Idempotency-Key` support
- Event bus: at-least-once delivery with deduplication at consumer

---

## 10. Audit Log (Every State Change)

### Audit Record Schema

```json
{
  "audit_id": "UUID",
  "trade_id": "UUID",
  "document_id": "UUID",
  "actor_id": "USER_UUID",
  "actor_role": "ENUM",
  "timestamp": "ISO8601",
  "action": "STRING (event name)",
  "before_hash": "SHA-256",
  "after_hash": "SHA-256",
  "diff_pointer": "URL (optional, to diff service)",
  "reason_code": "STRING (for exceptions, optional)",
  "ip": "STRING (optional)",
  "device": "STRING (optional)"
}
```

- Immutable append-only table
- Every state change produces an audit record
- Hash-based integrity: `before_hash` is hash of document at previous version, `after_hash` is hash after change
- Retention: minimum 7 years per compliance requirements

---

## 11. India-Specific vs Generic (Plugin Architecture)

### Strategy: India-Export-First MVP with Generic Base

| Module | Scope |
|--------|-------|
| **Generic (Base)** | UCP 600 LC rules, standard doc lifecycle, ISO currencies/countries, standard validations |
| **India Overlay** | GST/HSN validation on CI, FEMA purpose codes on Payment Advice, EDPMS integration for shipping bill closure, IEC format validation, 9-month realization deadline, caution listing alerts |

### Regime Selection

- Set at trade creation via `regulatory_context.regime`
- `INDIA_FEMA`: activates India overlay validations and EDPMS signals
- `GENERIC`: uses only base module validations
- Future: add overlays for UAE, Singapore, EU, etc.

---

## 12. MVP Scope Recommendation

### Phase 1 (MVP): Core Trade Flow

| Priority | Documents | Rationale |
|----------|-----------|-----------|
| **Must Have** | PI, CI, PL, BL/AWB, Payment Advice, FIRC | Minimum viable trade completion |
| **Must Have** | Export Declaration (India) | FEMA/EDPMS compliance |
| **Should Have** | PO/Contract, SI | Complete pre-shipment flow |
| **Could Have** | LC | Complex rules engine; many trades are non-LC |
| **Won't (MVP)** | COO, Import Declaration | Lane-dependent, destination-side |

### Phase 2: Full Suite

- LC with full UCP 600 rules engine
- COO with issuer API integration
- Import Declaration with country-specific customs
- Bank portal integrations
- OCR/extraction for BL/AWB

---

## Research Sources

- [UCP 600 Guide - Trade Finance Global](https://www.tradefinanceglobal.com/letters-of-credit/ucp-600-ultimate-guide/)
- [Understanding UCP 600 Rules 2025 - NNRV Trade](https://nnrvtradepartners.com/understanding-ucp-600-rules-for-trade-finance-transactions-in-2025-a-complete-guide/)
- [ClearEye ClearTrade - Trade Finance Compliance](https://cleareye.ai/solutions/)
- [Traydstream - Automated Document Checking](https://traydstream.com/)
- [AI-driven LC Document Examination - ScienceDirect](https://www.sciencedirect.com/science/article/pii/S2666954425000250)
- [EDPMS Guide for Exporters - EximPe](https://eximpe.com/blog/international-trade-finance/edpms-guide-for-exporters/)
- [RBI EDPMS/IDPMS Real-Time Monitoring - TaxGuru](https://taxguru.in/rbi/rbis-edpms-idpms-real-time-trade-monitoring.html)
- [FIRC Guide for Indian Exporters - Payoneer](https://www.payoneer.com/resources/news-events/decoding-firc-a-guide-for-indian-exporters-of-goods-services/)
- [Shipping Bill Tracking in EDPMS - EximPe](https://eximpe-blog.ghost.io/shipping-bill-status-tracking-closure-edpms/)
- [Event-Driven Architecture in Finance - Confluent](https://www.confluent.io/blog/event-driven-architecture-powers-finance-and-banking/)
- [Resilient EDA for FinServ - Temporal](https://temporal.io/blog/building-resilient-event-driven-architecture-for-finserv-with-temporal)
- [EDA in Banking - BOS Fintech](https://bosfintech.com/game-changer-in-banking-the-secrets-of-event-driven-architecture/)
