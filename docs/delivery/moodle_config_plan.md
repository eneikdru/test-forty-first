# Moodle Configuration Plan: HR & Finance Structure Specification

This document defines the formal configuration spike specification for implementing Moodle categories, role mappings, and metadata fields to support the Education Center of FBUN Central Research Institute of Epidemiology of Rospotrebnadzor.

## 1. Moodle Category Structure

To support the systematic storage of financial and HR documents under strict access control boundaries, the following hierarchical category IDs are established:

| Category Name | Moodle Category ID / Path | Purpose / Description |
| :--- | :--- | :--- |
| **Education Center Root** | `edu_center_root` | Top-level category for the educational center. |
| **├── Budget** | `edu_budget_finance` | Financial documents, budgets, and financial reporting. |
| **├── Workload** | `edu_staff_workload` | Faculty teaching hours, staff distributions, and workloads. |
| **├── Scholarships** | `edu_scholarships` | Student stipend structures, criteria, and scholarship orders. |
| **└── Reports** | `edu_academic_reports` | General institutional reporting, exam reports, and audits. |

---

## 2. Role Mappings

The corporate and educational roles from the requirements are mapped to standard Moodle system roles as follows:

| Target User Role (Russian Spec) | Standard Moodle Role Mapping | Description of Permissions / Capability Overrides |
| :--- | :--- | :--- |
| **Administrator** *(Администратор)* | `manager` / `admin` | Full Read/Write/Delete authority across all categories. Backup, restore, and audit log inspection. |
| **Content Manager** *(Контент-менеджер)* | `coursecreator` / `editingteacher` | Management of articles, files, versioning, and categorizations. No permission to edit user credentials or access system backups. |
| **Teacher / Research Supervisor** *(Преподаватель / научный руководитель)* | `teacher` (non-editing) | Full read access to templates, guidelines, and curriculum documents. Allowed to suggest revisions and upload student work feedback. |
| **Resident / Postgraduate Student / Listener** *(Ординатор / аспирант / слушатель)* | `student` | Restricted read-only access. Search and download capabilities for student templates and general scholarship info. |

---

## 3. Custom Metadata Fields & Search Indexes

To ensure high-performance full-text search with abbreviations (e.g., "ФБУН", "ГЭК", "ГИА", "ФГОС") and filters, resources must be annotated with the following metadata structure:

### 3.1. Database-Level Metadata Schema

| Custom Field Key | Data Type | Moodle Object Target | Allowed/Suggested Values |
| :--- | :--- | :--- | :--- |
| `doc_type` | `Text (Short)` | Course / Resource Option | `Regulations`, `Forms/Templates`, `Protocols`, `Curriculum`, `Guidelines` |
| `specialty` | `Text (Short)` | Course / Resource Option | `Epidemiology`, `Infectious Diseases`, `Pediatrics`, `Other` |
| `edu_level` | `Text (Short)` | Course / Resource Option | `Residency`, `Postgraduate`, `Additional Professional Education` |
| `update_date` | `Date` | Course / Resource Option | Automatically derived or manually specified timestamp |

### 3.2. Configured System Tags

All resources under the categories listed in Section 1 must support the following standard tags:
- `ординатура` (Residency)
- `аспирантура` (Postgraduate)
- `нормативные акты` (Regulations)
- `шаблоны` (Templates)
- `вопросы к экзаменам` (Exam Questions)
- `ФБУН` (FBUN)
- `ГЭК` (GEK)
- `ГИА` (GIA)
- `ФГОС` (FGOS)

---

## 4. Strict Access Control Matrix & Data Protection Requirements

This design enforces data protection requirements under federal privacy and security guidelines (e.g., FZ-152 on Personal Data).

### 4.1. The Access Control Matrix

| Category ID | Administrator | Content Manager | Teacher / Research Supervisor | Resident / Postgraduate / Listener |
| :--- | :---: | :---: | :---: | :---: |
| `edu_budget_finance` | **Full Read/Write** | **No Access** (Default Deny) | **No Access** (Default Deny) | **No Access** (Default Deny) |
| `edu_staff_workload` | **Full Read/Write** | **Read/Write** | **Read Only** (Templates only) | **No Access** (Default Deny) |
| `edu_scholarships` | **Full Read/Write** | **Read/Write** | **Read Only** | **Read Only** (General criteria only) |
| `edu_academic_reports` | **Full Read/Write** | **Read/Write** | **Read Only** | **No Access** (Default Deny) |

### 4.2. Data Protection Guarantees
1. **Default Deny Policy**: Access is strictly denied by default for all financial and personnel categories. Users must be explicitly mapped via cohort or individual enrolment.
2. **Access Isolation**: Student accounts are completely isolated from budget data (`edu_budget_finance`) and workload distributions (`edu_staff_workload`). No PII or financial projections are queryable or visible to general students.
3. **Audit Trails**: All modifications, file downloads, and configuration updates within the `edu_budget_finance` category are captured in the system access logs (`moodle_log`).
4. **Data Minimization**: Document templates must not contain real personnel financial information or unencrypted PII.

---

## 5. Philosophical Grounding Proof Obligations (Hillary Putnam - Pragmatic Realism)

This specification is validated through Putnam's Pragmatic Realism lenses to eliminate conceptual and architectural errors:

### 5.1. Holism Impact Map (`HILLARI_PATNEM_01_HOLISM_IMPACT_MAP`)
- **Action**: Locally introduced category hierarchies and role definitions.
- **Affected Neighbors**:
  1. *E2E Test Suite*: Verification tests must authenticate as distinct roles and verify security boundaries.
  2. *API Automation Scripts*: Scripts must use exact category IDs (`edu_budget_finance`, etc.) when provisioning the database.
- **Evidence**: Boundaries mapped in Section 4.1 enforce strict separation at the schema level.

### 5.2. Inferential Scoreboard (`HILLARI_PATNEM_03_INFERENTIAL_SCOREBOARD`)
- **Commitments**:
  - The ID `edu_budget_finance` must reject access requests from anyone lacking the `admin` or designated role.
- **Entitlement Evidence**: Moodle's capability checking framework provides authorization checks prior to file serving.
- **Incompatibility Checks**: A role cannot simultaneously be a general `student` and gain read entitlement in `edu_budget_finance`.

### 5.3. Category Error Scan (`HILLARI_PATNEM_15_CATEGORY_ERROR_SCAN`)
- **Precaution**: We do not treat a policy rule as active code or runtime data. Instead, permissions are modeled statically in Moodle's capability schema, with strict mapping interfaces.
- **Preservation Boundary**: The role definitions explicitly map conceptual roles to system-level role capability definitions, avoiding arbitrary permission assignments.

### 5.4. Conversation Maxim (`HILLARI_PATNEM_18_CONVERSATION_MAXIM`)
- **Downstream Consumer**: `BARCAN-TAG-02` (Moodle API Automation Script developer).
- **Minimal Required Fields Provided**: Exact category IDs (Section 1), mapped standard Moodle roles (Section 2), custom fields keys (Section 3).
- **Actionable Next Step**: Implement standard Moodle API calls (`core_course_create_categories`, `core_role_assign`) to construct this structure deterministically.

### 5.5. Constructive Proof Object (`HILLARI_PATNEM_20_CONSTRUCTIVE_PROOF_OBJECT`)
- **Proof Value**: This markdown file serves as the strict, unambiguous source of truth.
- **Evidence Needed**: The automation script and E2E test suite are verified strictly against the IDs, roles, and schema keys defined herein.

---

## 6. Handoff Note & Delivery Decision

- **Delivery Decision**: Formalized Moodle configuration structure spike completed successfully.
- **Next Owner Role**: `BARCAN-TAG-02` (Technical Lead / Developer for Moodle API Automation Script).
- **Verification Plan**: Verification is conducted by reading this markdown file and asserting compliance with the specification during downstream scripting and E2E testing.
- **No Implementation Scope Expansion**: The spike defines *only* the minimum configuration and access mapping boundaries required for the HR/Finance slice. Adjacent LMS structures are omitted.
