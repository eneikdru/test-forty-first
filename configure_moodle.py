#!/usr/bin/env python3
"""
Moodle API Automation Script
Configures categories and role permissions in Moodle deterministically.
Now fully idempotent and safe to run multiple times.
"""

import sys
import json
import argparse
import urllib.request
import urllib.parse


def make_moodle_request(api_url, token, ws_function, data_dict=None):
    """
    Makes a POST request to Moodle's REST API endpoint with x-www-form-urlencoded body.
    """
    full_url = f"{api_url}?wstoken={token}&wsfunction={ws_function}&moodlewsrestformat=json"

    encoded_data = b""
    if data_dict:
        encoded_data = urllib.parse.urlencode(data_dict).encode("utf-8")

    req = urllib.request.Request(full_url, data=encoded_data, method="POST")
    req.add_header("Content-Type", "application/x-www-form-urlencoded")

    try:
        with urllib.request.urlopen(req) as response:
            res_body = response.read().decode("utf-8")
            return json.loads(res_body)
    except Exception as e:
        print(f"Error calling Moodle API function '{ws_function}': {e}", file=sys.stderr)
        raise


def fetch_existing_categories(api_url, token):
    """
    Fetches the existing categories list and returns a dict mapping idnumber to its DB id.
    """
    try:
        response = make_moodle_request(api_url, token, "core_course_get_categories")
        category_map = {}
        if response and isinstance(response, list):
            for cat in response:
                idnumber = cat.get("idnumber")
                cat_id = cat.get("id")
                if idnumber and cat_id is not None:
                    category_map[idnumber] = int(cat_id)
        return category_map
    except Exception as e:
        print(f"Warning: Failed to fetch existing categories from Moodle: {e}. Assuming empty instance.", file=sys.stderr)
        return {}


def configure_moodle(api_url, token):
    print(f"Starting idempotent Moodle configuration at {api_url}...")

    # Fetch existing categories for idempotency check
    existing_categories = fetch_existing_categories(api_url, token)

    # 1. Create/Retrieve top-level category: edu_center_root
    if "edu_center_root" in existing_categories:
        root_id = existing_categories["edu_center_root"]
        print(f"Root category 'edu_center_root' already exists with ID: {root_id}")
    else:
        print("Creating Education Center Root category...")
        root_data = {
            "categories[0][name]": "Education Center Root",
            "categories[0][idnumber]": "edu_center_root",
            "categories[0][parent]": "0",
            "categories[0][description]": "Top-level category for the educational center."
        }
        root_response = make_moodle_request(api_url, token, "core_course_create_categories", root_data)
        if not root_response or not isinstance(root_response, list) or "id" not in root_response[0]:
            raise ValueError(f"Failed to create root category. Response: {root_response}")
        root_id = root_response[0]["id"]
        print(f"Education Center Root created successfully with ID: {root_id}")

    # 2. Create/Retrieve subcategories under edu_center_root
    subcategories = [
        {
            "name": "Budget",
            "idnumber": "edu_budget_finance",
            "description": "Financial documents, budgets, and financial reporting."
        },
        {
            "name": "Workload",
            "idnumber": "edu_staff_workload",
            "description": "Faculty teaching hours, staff distributions, and workloads."
        },
        {
            "name": "Scholarships",
            "idnumber": "edu_scholarships",
            "description": "Student stipend structures, criteria, and scholarship orders."
        },
        {
            "name": "Reports",
            "idnumber": "edu_academic_reports",
            "description": "General institutional reporting, exam reports, and audits."
        }
    ]

    category_ids = {}
    for sub in subcategories:
        idnum = sub["idnumber"]
        if idnum in existing_categories:
            category_ids[idnum] = existing_categories[idnum]
            print(f"Category '{sub['name']}' ({idnum}) already exists with ID: {existing_categories[idnum]}")
        else:
            print(f"Creating category: {sub['name']} ({idnum})...")
            sub_data = {
                "categories[0][name]": sub["name"],
                "categories[0][idnumber]": idnum,
                "categories[0][parent]": str(root_id),
                "categories[0][description]": sub["description"]
            }
            sub_res = make_moodle_request(api_url, token, "core_course_create_categories", sub_data)
            if not sub_res or not isinstance(sub_res, list) or "id" not in sub_res[0]:
                raise ValueError(f"Failed to create subcategory '{idnum}'. Response: {sub_res}")
            category_ids[idnum] = sub_res[0]["id"]
            print(f"Category '{sub['name']}' created with ID: {sub_res[0]['id']}")

    # 3. Apply strict role assignments (strict permissions) per category context
    print("Applying strict permissions/role assignments per category context...")

    # Map roles based on Access Control Matrix (Section 4.1 of moodle_config_plan.md)
    # Admin (Role ID 1) -> all
    # Content Manager (Role ID 2) -> Workload, Scholarships, Reports
    # Teacher (Role ID 3) -> Workload, Scholarships, Reports
    # Student (Role ID 4) -> Scholarships

    assignments = [
        # Admin (User 10)
        {"roleid": 1, "userid": 10, "contextid": category_ids["edu_budget_finance"]},
        {"roleid": 1, "userid": 10, "contextid": category_ids["edu_staff_workload"]},
        {"roleid": 1, "userid": 10, "contextid": category_ids["edu_scholarships"]},
        {"roleid": 1, "userid": 10, "contextid": category_ids["edu_academic_reports"]},
        # Content Manager (User 20)
        {"roleid": 2, "userid": 20, "contextid": category_ids["edu_staff_workload"]},
        {"roleid": 2, "userid": 20, "contextid": category_ids["edu_scholarships"]},
        {"roleid": 2, "userid": 20, "contextid": category_ids["edu_academic_reports"]},
        # Teacher (User 30)
        {"roleid": 3, "userid": 30, "contextid": category_ids["edu_staff_workload"]},
        {"roleid": 3, "userid": 30, "contextid": category_ids["edu_scholarships"]},
        {"roleid": 3, "userid": 30, "contextid": category_ids["edu_academic_reports"]},
        # Student (User 40)
        {"roleid": 4, "userid": 40, "contextid": category_ids["edu_scholarships"]}
    ]

    for assign in assignments:
        print(f"Assigning role {assign['roleid']} to user {assign['userid']} in category context {assign['contextid']}...")
        assign_data = {
            "assignments[0][roleid]": str(assign["roleid"]),
            "assignments[0][userid]": str(assign["userid"]),
            "assignments[0][contextid]": str(assign["contextid"])
        }
        make_moodle_request(api_url, token, "core_role_assign", assign_data)

    print("Moodle deterministic configuration completed successfully! 🎉")


def main():
    parser = argparse.ArgumentParser(description="Configure Moodle categories and roles/permissions via REST API.")
    parser.add_argument("--url", required=True, help="Moodle REST API server URL (e.g., http://localhost/webservice/rest/server.php)")
    parser.add_argument("--token", required=True, help="Moodle API web service Token")

    args = parser.parse_args()
    try:
        configure_moodle(args.url, args.token)
    except Exception as e:
        print(f"Configuration failed: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
