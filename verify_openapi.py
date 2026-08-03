#!/usr/bin/env python3
"""
verify_openapi.py

Loads and validates docs/openapi.yaml and docs/openapi.json
to ensure structure, validity, and conformity with Eneik constraints.
"""

import os
import sys
import json
import yaml

def validate_openapi():
    yaml_path = "docs/openapi.yaml"
    json_path = "docs/openapi.json"

    # 1. Assert file existence
    if not os.path.exists(yaml_path):
        print(f"Error: YAML contract not found at {yaml_path}")
        sys.exit(1)
    if not os.path.exists(json_path):
        print(f"Error: JSON contract not found at {json_path}")
        sys.exit(1)

    # 2. Parse and validate YAML structure
    try:
        with open(yaml_path, 'r', encoding='utf-8') as yf:
            yaml_data = yaml.safe_load(yf)
            print("Successfully parsed OpenAPI YAML file structure.")
    except Exception as e:
        print(f"Validation Error in YAML parser: {e}")
        sys.exit(1)

    # 3. Parse and validate JSON structure
    try:
        with open(json_path, 'r', encoding='utf-8') as jf:
            json_data = json.load(jf)
            print("Successfully parsed OpenAPI JSON file structure.")
    except Exception as e:
        print(f"Validation Error in JSON parser: {e}")
        sys.exit(1)

    # 4. Verify semantic parity
    # Strip down versions to basic comparable strings / object identity
    yaml_dump = json.dumps(yaml_data, sort_keys=True)
    json_dump = json.dumps(json_data, sort_keys=True)
    if yaml_dump != json_dump:
        print("Warning: Minor structural differences detected between JSON and YAML. Normalizing...")
        # Deep compare to see if they hold the exact same dict keys/values
        if yaml_data != json_data:
            print("Parity Error: YAML and JSON structures do not represent the exact same model!")
            sys.exit(1)
    print("YAML and JSON specs have 100% semantic parity.")

    # 5. Check Eneik domain specifics
    schemas = yaml_data.get("components", {}).get("schemas", {})

    # Check roles
    user_role_enum = schemas.get("UserRole", {}).get("enum", [])
    required_roles = ["Administrator", "Content-manager", "Teacher", "Student"]
    for role in required_roles:
        if role not in user_role_enum:
            print(f"Validation Error: Required role '{role}' is missing from UserRole schema.")
            sys.exit(1)
    print("All required user roles are present in the schema.")

    # Check categories
    category_enum = schemas.get("CategoryId", {}).get("enum", [])
    required_categories = ["edu_center_root", "edu_budget_finance", "edu_staff_workload", "edu_scholarships", "edu_academic_reports"]
    for cat in required_categories:
        if cat not in category_enum:
            print(f"Validation Error: Required category '{cat}' is missing from CategoryId schema.")
            sys.exit(1)
    print("All required Moodle category IDs are present in the schema.")

    # Check document types
    doc_types = schemas.get("DocumentType", {}).get("enum", [])
    required_types = ["Regulations", "Forms/Templates", "Protocols", "Curriculum", "Guidelines"]
    for t in required_types:
        if t not in doc_types:
            print(f"Validation Error: Required document type '{t}' is missing from DocumentType schema.")
            sys.exit(1)
    print("All required custom metadata document types are present in the schema.")

    print("\nOpenAPI Contract validation complete: ALL CHECKS PASS! 🎉")
    sys.exit(0)

if __name__ == "__main__":
    validate_openapi()
