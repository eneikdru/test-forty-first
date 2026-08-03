import pytest
import re
from playwright.sync_api import sync_playwright

@pytest.fixture(scope="module")
def playwright_instance():
    with sync_playwright() as p:
        yield p

@pytest.fixture(scope="module")
def browser(playwright_instance):
    browser = playwright_instance.chromium.launch(headless=True)
    yield browser
    browser.close()

@pytest.fixture
def page(browser):
    context = browser.new_context()
    page = context.new_page()
    yield page
    context.close()

def test_student_edit_denied(page):
    # Navigate to the frontend dev server
    page.goto("http://localhost:3000")
    page.wait_for_timeout(1000)

    # Pre-checks: ensure we are on the login page
    assert page.locator("text=Образовательный центр").is_visible()

    # Select the 'Student' role
    page.select_option("#role", "Student")
    page.wait_for_timeout(500)

    # Click the login button
    page.get_by_role("button", name="Войти в систему").click()
    page.wait_for_timeout(1000)

    # Verify we are on the dashboard as a Student
    assert page.locator("text=Ординатор / аспирант / слушатель").is_visible()

    # Click on the first document to open the details drawer
    page.get_by_role("button", name="Просмотреть подробности документа ФГОС ВО по специальности Эпидемиология").click()
    page.wait_for_timeout(1000)

    # Find the "Редактировать" button and click it
    edit_btn = page.locator("#edit-doc-btn")
    assert edit_btn.is_visible()
    edit_btn.click()
    page.wait_for_timeout(1000)

    # Verify that access is denied (error message is shown)
    error_msg = page.locator("#edit-error-msg")
    assert error_msg.is_visible()

    error_text = error_msg.inner_text()
    assert "Доступ запрещен" in error_text
    assert "ординатор" in error_text.lower() or "аспирант" in error_text.lower()

    # Ensure edit input fields are NOT displayed
    assert not page.locator("#edit-name-input").is_visible()

def test_student_budget_access_denied(page):
    # Navigate to frontend dev server
    page.goto("http://localhost:3000")
    page.wait_for_timeout(1000)

    # Login as Student
    page.select_option("#role", "Student")
    page.get_by_role("button", name="Войти в систему").click()
    page.wait_for_timeout(1000)

    # Click on the Budget document to open details
    page.get_by_role("button", name="Просмотреть подробности документа Годовой финансовый бюджет центра на 2026 год").click()
    page.wait_for_timeout(1000)

    # Access denied container must be visible
    assert page.locator("#budget-access-denied").is_visible()
    assert "Доступ ограничен" in page.locator("#budget-access-denied").inner_text()

    # Edit, download buttons should be hidden
    assert not page.locator("#edit-doc-btn").is_visible()
    assert not page.locator("#download-pdf-btn").is_visible()
    assert not page.locator("#download-docx-btn").is_visible()

def test_economist_budget_access_granted(page):
    # Navigate to frontend dev server
    page.goto("http://localhost:3000")
    page.wait_for_timeout(1000)

    # Login as Economist
    page.select_option("#role", "Economist")
    page.get_by_role("button", name="Войти в систему").click()
    page.wait_for_timeout(1000)

    # Click on the Budget document to open details
    page.get_by_role("button", name="Просмотреть подробности документа Годовой финансовый бюджет центра на 2026 год").click()
    page.wait_for_timeout(1000)

    # Access denied container must NOT be visible
    assert not page.locator("#budget-access-denied").is_visible()

    # Download buttons and edit button must be visible
    assert page.locator("#edit-doc-btn").is_visible()
    assert page.locator("#download-pdf-btn").is_visible()
    assert page.locator("#download-docx-btn").is_visible()

    # Edit document
    page.locator("#edit-doc-btn").click()
    page.wait_for_timeout(1000)

    # Fill in new title and description
    page.fill("#edit-name-input", "Годовой финансовый бюджет центра на 2026 год - ОБНОВЛЕН")
    page.fill("#edit-description-input", "Новая отредактированная аннотация бюджета.")
    page.locator("#save-edit-btn").click()
    page.wait_for_timeout(1000)

    # Verify changes successfully applied
    assert page.locator("#edit-success-msg").is_visible()
    assert "успешно" in page.locator("#edit-success-msg").inner_text()
    assert page.locator("h3#drawer-title").is_visible()
    assert "ОБНОВЛЕН" in page.locator("h3#drawer-title").inner_text()

def test_no_english_placeholders(page):
    # Navigate to frontend
    page.goto("http://localhost:3000")
    page.wait_for_timeout(1000)

    # We want to check login page inputs
    placeholders = []

    # Grab all elements with placeholder attribute
    elements = page.query_selector_all("[placeholder]")
    for el in elements:
        ph = el.get_attribute("placeholder")
        if ph:
            placeholders.append(ph)

    # Now let's login as Administrator to expose remaining fields (comments, actualization, edit)
    page.select_option("#role", "Administrator")
    page.get_by_role("button", name="Войти в систему").click()
    page.wait_for_timeout(1000)

    # Open a document to expose drawer
    page.get_by_role("button", name="Просмотреть подробности документа ФГОС ВО по специальности Эпидемиология").click()
    page.wait_for_timeout(1000)

    # Open edit mode
    page.locator("#edit-doc-btn").click()
    page.wait_for_timeout(1000)

    # Grab placeholders again
    elements = page.query_selector_all("[placeholder]")
    for el in elements:
        ph = el.get_attribute("placeholder")
        if ph and ph not in placeholders:
            placeholders.append(ph)

    # Close edit mode
    page.locator("#cancel-edit-btn").click()
    page.wait_for_timeout(500)

    # Check that all gathered placeholders contain no English words.
    # We define English word as an alphabetical sequence of 3 or more characters,
    # and we specifically allow common technical terms like '.ru' or 'epidem.ru'.
    english_word_pattern = re.compile(r'\b[a-zA-Z]{3,}\b')

    for ph in placeholders:
        # Filter out email domain or specific allowed email components
        cleaned_ph = ph.replace("epidem.ru", "").replace(".ru", "")
        matches = english_word_pattern.findall(cleaned_ph)

        # Verify no English words were matched
        assert len(matches) == 0, f"Found English placeholder text or words: '{matches}' in placeholder '{ph}'"
