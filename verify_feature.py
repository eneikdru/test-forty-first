#!/usr/bin/env python3
"""
verify_feature.py

Automated Playwright E2E verification test for:
1. Student role document edit access boundaries.
2. English placeholder text scanning.
"""

import re
import sys
from playwright.sync_api import sync_playwright, expect

def run_tests():
    print("Starting Playwright E2E Verification Tests...", flush=True)
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(viewport={"width": 1280, "height": 800})
        page = context.new_page()

        # Step 1: Navigate to the web application
        print("Navigating to http://localhost:3000/...", flush=True)
        page.goto("http://localhost:3000/")
        page.wait_for_load_state("networkidle")

        # Step 2: Select the "Student" role and log in
        print("Logging in as Student (Ординатор / аспирант / слушатель)...", flush=True)
        page.select_option("#role", value="Student")
        page.click("button:has-text('Войти в систему')")
        page.wait_for_timeout(500)

        # Assert Student name or role is shown
        expect(page.locator("text=Ординатор / аспирант / слушатель")).to_be_visible()

        # Step 3: Open details of the first document card
        print("Opening details for the first document...", flush=True)
        first_doc = page.locator("text=ФГОС ВО по специальности Эпидемиология").first
        expect(first_doc).to_be_visible()
        first_doc.click()
        page.wait_for_timeout(500)

        # Step 4: Click the Edit Metadata button
        print("Attempting to edit document as Student...", flush=True)
        edit_btn = page.locator("#edit-btn")
        expect(edit_btn).to_be_visible()
        edit_btn.click()
        page.wait_for_timeout(500)

        # Step 5: Assert that access is denied (Доступ запрещен error message)
        print("Asserting access-denied message is displayed...", flush=True)
        edit_error = page.locator("#edit-error")
        expect(edit_error).to_be_visible()
        expect(edit_error).to_contain_text("Доступ запрещен")
        print("✓ SUCCESS: Student access to edit was successfully denied with message:", edit_error.inner_text(), flush=True)

        # Capture screenshot of the access-denied state
        screenshot_path = "/home/jules/verification/verification.png"
        page.screenshot(path=screenshot_path)
        print(f"✓ Screenshot captured at {screenshot_path}", flush=True)

        # Step 6: Scan all input and textarea elements on the page for English placeholder texts
        print("Scanning page inputs and textareas for English placeholders...", flush=True)
        inputs = page.query_selector_all("input, textarea")
        for idx, element in enumerate(inputs):
            placeholder = element.get_attribute("placeholder")
            if placeholder:
                # Assert that there are no English letters [a-zA-Z] inside the placeholder
                if re.search(r"[a-zA-Z]", placeholder):
                    print(f"✗ FAIL: Element {element.get_attribute('id') or element.get_attribute('name') or idx} has English placeholder: '{placeholder}'", flush=True)
                    sys.exit(1)
                else:
                    print(f"  - Element {element.get_attribute('id') or element.get_attribute('name') or idx} placeholder: '{placeholder}' (Local Only)", flush=True)
        print("✓ SUCCESS: All placeholder texts are completely free of English text!", flush=True)

        # Close the details drawer to avoid overlay interception
        print("Closing document details drawer...", flush=True)
        page.click("button[aria-label='Закрыть']")
        page.wait_for_timeout(500)

        # Step 7: Log out, and log in as Administrator to verify that editing is allowed for authorized roles
        print("Logging out student user...", flush=True)
        page.click("button[aria-label='Выйти']")
        page.wait_for_timeout(500)

        print("Logging in as Administrator...", flush=True)
        page.select_option("#role", value="Administrator")
        page.click("button:has-text('Войти в систему')")
        page.wait_for_timeout(500)

        # Open the first document
        print("Opening the document as Administrator...", flush=True)
        page.click("text=ФГОС ВО по специальности Эпидемиология")
        page.wait_for_timeout(500)

        # Click edit button
        page.click("#edit-btn")
        page.wait_for_timeout(500)

        # Update the document name
        new_name = "ФГОС ВО по специальности Эпидемиология (ИЗМЕНЕН)"
        print(f"Updating name to: {new_name}...", flush=True)
        page.fill("#edit-name", new_name)
        page.click("#save-edit-btn")
        page.wait_for_timeout(500)

        # Verify edited name on the details drawer and on the main page
        drawer_title = page.locator("#drawer-title")
        expect(drawer_title).to_have_text(new_name)
        print(f"✓ SUCCESS: Document was successfully edited by Administrator!", flush=True)

        print("All verification tests passed successfully!", flush=True)
        browser.close()

if __name__ == "__main__":
    run_tests()
