# ADR 003: Document Export Library Evaluation and Selection

## Status
Approved

## Context
The Educational Center Knowledge Base of FBUN Central Research Institute of Epidemiology of Rospotrebnadzor requires robust, high-performance, and maintainable document export capabilities. Users must be able to export educational materials, curriculum, guidelines, and examination protocols into PDF and DOCX formats directly from the search interface (addressing PR 63 client requirements and regulatory compliance).

As part of the Architecture Spike for Document Export Libraries (`BARCAN-TAG-09`), we must evaluate the available Java libraries for both PDF and DOCX generation. The selected options must fit our architecture, comply with the Cynefin "complex" domain (where we probe, sense, and respond), and meet the Kano "Performance" category to ensure swift, secure, and resource-efficient generation on the Spring Boot backend.

---

## 1. Evaluation of PDF Generation Libraries

We evaluated three primary library patterns for PDF generation:

### 1.1. OpenHTMLtoPDF / Flying Saucer (HTML-to-PDF Converter)
- **Mechanism**: Renders well-formed XHTML and modern CSS (CSS3 Paged Media) into PDF.
- **Pros**:
  - Extremely high separation of concerns: templates are designed in standard HTML/CSS.
  - Excellent styling support (fonts, layouts, headers, footers, page numbering, tables).
  - Perfect for Russian language localization, as it supports custom TrueType (TTF) and OpenType fonts (e.g., Inter, JetBrains Mono) dynamically loaded from classpath.
- **Cons**:
  - Requires input to be strictly compliant XHTML.
  - Slight memory overhead during the HTML parsing phase compared to direct drawing.
- **Kano Performance Rating**: High. Drastically reduces template maintenance time while delivering high-speed render execution.

### 1.2. Apache PDFBox
- **Mechanism**: Low-level document builder API that directly manipulates the PDF stream.
- **Pros**:
  - Extremely lightweight with minimal memory footprints.
  - Complete, direct control over the canvas coordinates.
- **Cons**:
  - Writing code is highly procedural and verbose. Something as simple as paragraph text-wrapping or table cell auto-sizing must be calculated manually in Java code.
  - Very poor maintainability for complex layouts; making visual design changes requires changing compiled Java code.
- **Kano Performance Rating**: Low for developer velocity, High for execution speed.

### 1.3. OpenPDF (iText Fork)
- **Mechanism**: Programmatic document builder API using high-level layout elements (Paragraph, Table, Cell).
- **Pros**:
  - Fork of LGPL-licensed iText 4, keeping it free from commercial licensing constraints.
  - Easier layout model than PDFBox (handles standard text-wrapping and tables natively).
- **Cons**:
  - Styling and layouts are still programmed imperatively in Java, making layout updates slow to implement and test.
  - Lacks native support for complex modern CSS-based designs without manually building structural components.

---

## 2. Evaluation of DOCX Generation Libraries

We evaluated two primary library patterns for DOCX generation:

### 2.1. Apache POI (XWPF Component)
- **Mechanism**: Direct programmatic manipulation of Office Open XML (OOXML) files.
- **Pros**:
  - Lightweight, standard Apache project with zero external dependencies.
  - Simple, clean API to create paragraphs, runs, styles, tables, and document properties programmatically.
  - Very fast execution times with minimal memory footprints.
- **Cons**:
  - Direct styling can be verbose, but can be easily wrapped in custom utility classes.
- **Kano Performance Rating**: High. Delivers fast generation of structured Word documents without bloating the application.

### 2.2. docx4j
- **Mechanism**: JAXB-based enterprise-grade XML manipulation library for OOXML.
- **Pros**:
  - Comprehensive feature set covering every advanced detail of the Word document structure.
  - Allows deep manipulation of underlying XML schemas.
- **Cons**:
  - Extremely heavy memory overhead due to extensive JAXB object tree serialization.
  - High complexity, long learning curve, and slow startup times.
- **Kano Performance Rating**: Low. Adds unnecessary overhead (Muda) for the standard templates and tables needed by our educational system.

---

## 3. Comparative Trade-Off Matrix

| Dimension | OpenHTMLtoPDF | Apache PDFBox | OpenPDF | Apache POI | docx4j |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Output Format** | PDF | PDF | PDF | DOCX | DOCX |
| **Developer Velocity** | **Excellent** | Poor | Moderate | **Excellent** | Poor |
| **Styling Flexibility** | **Excellent** | Poor | Moderate | Moderate | Excellent |
| **Memory Footprint** | Moderate | **Minimal** | Low | **Minimal** | High |
| **Font Customization** | Easy (CSS) | Manual | Manual | Native | Complex |
| **Complexity (Cynefin)**| Low | High | Moderate | **Low** | High |

---

## 4. Architectural Decision and Rationale

We decide to select **OpenHTMLtoPDF** for PDF generation and **Apache POI (XWPF)** for DOCX generation.

### Rationale:
1. **Separation of Concerns (Knowing-That vs. Knowing-How)**: In accordance with our architectural philosophy, our controller must declare *what* should happen, and delegate the procedural *how* of document compilation. Using OpenHTMLtoPDF allows our templates to be declared externally as HTML files, keeping the service layer purely focused on data injection and export execution.
2. **Kano Performance Model**:
   - For PDF: The combination of HTML templates and CSS ensures we can easily style our documents with the brand typography tokens (Inter, JetBrains Mono) and brand color (`#1A365D`) defined by our design specs. Doing this dynamically via CSS dramatically outperforms manual canvas coordinate mapping.
   - For DOCX: Apache POI (XWPF) is highly performant and lightweight, allowing our system to quickly compile structured schedules and guidelines with minimal RAM footprint.
3. **Pragmatic Realism (Putnam)**: This choice avoids speculative complexity (Muda). It keeps our codebase simple, maintainable, and highly testable, which is critical in our Cynefin complex domain of rapid software delivery.

---

## 5. Architectural Consequences and Next Steps
- **Spring Boot Dependencies**: The following dependencies are to be added to `pom.xml` during implementation:
  - `openhtmltopdf-pdfbox` (for HTML-to-PDF rendering)
  - `poi-ooxml` (for DOCX rendering)
- **Handoff Decision**: This spike completes the critical technical decision. The next implementation slice (BARCAN-TAG-09) is unblocked to write the controller endpoints and service implementations to serve the real export files.
