# Delivery Plan: Document Export Spike & Evaluation Specification

This document defines the formal delivery plan for document export spikes, library evaluations, and delivery decisions to support the Educational Center of FBUN Central Research Institute of Epidemiology of Rospotrebnadzor.

## 1. Minimal Delivery Decision Needed to Unblock Implementation

To unblock the follow-up wishlist item (implementation of document export endpoints and frontend connectivity), we establish the smallest necessary delivery decisions:

1. **Selected Libraries**: **OpenHTMLtoPDF** for PDF, **Apache POI (XWPF)** for DOCX.
2. **Template Storage Location**: Templates will be placed under a dedicated resources folder: `src/main/resources/templates/export/`.
3. **Template Format**:
   - PDF templates: XHTML + CSS files (containing typography custom mappings for Inter and JetBrains Mono, primary brand color `#1A365D`, and strict Russian language only).
   - DOCX templates: Pre-styled base `.docx` templates or completely programmatic builders using a standardized POI utility helper to avoid hardcoded formatting rules.

---

## 2. Category Structure and Access Controls

To support the secure download of files based on user categories and role mappings (complying with the requirements mapped in `docs/delivery/moodle_config_plan.md`):

### 2.1. File Security and Allowed Download Channels

| Category ID | Moodle Category Path | Access Control Rule (Download Blockers) |
| :--- | :--- | :--- |
| `edu_budget_finance` | Budget & Finance | Restricted to **Administrators** only. Attempted downloads by Teachers, Students, or Content Managers must return a `403 Forbidden` error immediately. |
| `edu_staff_workload`| Workloads | Restricted to **Administrators**, **Content Managers**, and **Teachers** (Read-Only). General Students are blocked with a `403 Forbidden` error. |
| `edu_scholarships`  | Scholarships | Allowed for **Administrators**, **Content Managers** (Read/Write), **Teachers**, and **Students** (Read-Only). |
| `edu_academic_reports`| Academic Reports | Restricted to **Administrators**, **Content Managers**, and **Teachers**. General Students are blocked. |

---

## 3. Metadata Mapping & Query Options

Export endpoints must map document properties to metadata to support targeted generation:

- **Endpoint**: `/api/v1/documents/{id}/export`
- **Supported Parameters**:
  - `format`: Must be `pdf` or `docx` (Closed enum `ACP-007`).
  - `version`: Optional version query (default: current latest version).
- **Audit Logging**: Every export request must trigger a transactional log entry under the `AUDIT_LOG` system table:
  - `DOCUMENT_EXPORT` action, resource UUID, operator ID, and UTC timestamp.

---

## 4. Philosophical Grounding Proof Obligations (Hillary Putnam - Pragmatic Realism)

We execute Putnam's Pragmatic Realism checks to verify the completeness and correctness of this delivery specification:

### 4.1. Holism Impact Map (`HILLARI_PATNEM_01_HOLISM_IMPACT_MAP`)
- **Action**: Selecting OpenHTMLtoPDF and Apache POI.
- **Affected Neighbors**:
  - *Maven Dependencies*: `pom.xml` must receive `openhtmltopdf-pdfbox` and `poi-ooxml`.
  - *Frontend Controls*: Download button action handlers in `DocumentSearch.svelte` must route requests to `/api/v1/documents/{id}/export?format={pdf|docx}` instead of triggering a generic JavaScript alert.
- **Evidence**: Mapped boundaries prevent visual defects or unexpected behavior.

### 4.2. Inferential Scoreboard (`HILLARI_PATNEM_03_INFERENTIAL_SCOREBOARD`)
- **Commitments**:
  - The export system must output valid PDF and DOCX documents with embedded corporate font configuration.
- **Entitlement Evidence**: Svelte components can dynamically pass JWT tokens to verify the download authorization block.
- **Incompatibility Checks**: A user authenticated as a student must not be allowed to invoke `GET /api/v1/documents/{budget_id}/export`.

### 4.3. Category Error Scan (`HILLARI_PATNEM_15_CATEGORY_ERROR_SCAN`)
- **Precaution**: We do not hardcode static secrets or mocked credentials to bypass authentication during export development. PII is completely isolated from the templates.
- **Preservation Boundary**: Exported PDFs use standard translation files mapping database fields to Russian language strings dynamically, preserving schema boundaries.

### 4.4. Conversation Maxim (`HILLARI_PATNEM_18_CONVERSATION_MAXIM`)
- **Downstream Consumer**: `BARCAN-TAG-00` / `BARCAN-TAG-02` (Backend and API integration developers).
- **Minimal Required Fields Provided**: Target library selection, category mapping rules (Section 2.1), and export API endpoints definition (Section 3).
- **Actionable Next Step**: Create backend controllers and services that consume the template files and invoke the PDF/DOCX compiler APIs.

### 4.5. Constructive Proof Object (`HILLARI_PATNEM_20_CONSTRUCTIVE_PROOF_OBJECT`)
- **Proof Value**: This markdown file is checked and finalized.
- **Evidence Needed**: Direct compliance of downstream API endpoints with the category access mapping rules specified here.

---

## 5. Handoff Note & Delivery Decision

- **Delivery Decision**: Formalized document export delivery plan and library selection finalized.
- **Next Owner Role**: **BARCAN-TAG-00** / **BARCAN-TAG-02** (Technical Developer)
- **Target Slice**: Document Export API Implementation.
- **Action Required**: Add dependencies to `pom.xml` and implement the `/api/v1/documents/{id}/export` endpoint following the access control matrix.
