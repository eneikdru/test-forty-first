#!/usr/bin/env python3
"""
sync_openapi.py

Reads docs/openapi.yaml, parses it, and writes out docs/openapi.json
to guarantee 100% semantic parity between them.
"""

import os
import sys
import json
import yaml

def main():
    yaml_path = "docs/openapi.yaml"
    json_path = "docs/openapi.json"

    if not os.path.exists(yaml_path):
        print(f"Error: YAML contract not found at {yaml_path}")
        sys.exit(1)

    try:
        with open(yaml_path, 'r', encoding='utf-8') as yf:
            yaml_data = yaml.safe_load(yf)
        print("Successfully read openapi.yaml")
    except Exception as e:
        print(f"Error reading/parsing YAML: {e}")
        sys.exit(1)

    try:
        # Write back to JSON file ensuring exact semantic parity
        with open(json_path, 'w', encoding='utf-8') as jf:
            json.dump(yaml_data, jf, indent=2, ensure_ascii=False)
        print("Successfully synchronized docs/openapi.json with 100% parity.")
    except Exception as e:
        print(f"Error writing/parsing JSON: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
