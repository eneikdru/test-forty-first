import json
import pytest
from unittest.mock import patch, MagicMock
from io import BytesIO
from configure_moodle import configure_moodle, make_moodle_request


@pytest.fixture
def mock_moodle_api():
    """
    Mocks urllib.request.urlopen to simulate a Moodle API server.
    """
    with patch("urllib.request.urlopen") as mock_url_open:
        def side_effect(req):
            url = req.full_url
            data_bytes = req.data
            data = data_bytes.decode("utf-8") if data_bytes else ""
            params = urllib_parse_qs(data)

            # Retrieve wsfunction from URL
            ws_function = None
            for part in url.split("&"):
                if part.startswith("wsfunction="):
                    ws_function = part.split("=")[1]
                    break

            if ws_function == "core_course_get_categories":
                # Mock get_categories returned list
                response_data = [
                    {"id": 100, "idnumber": "edu_center_root", "name": "Education Center Root"}
                ]
            elif ws_function == "core_course_create_categories":
                name = params.get("categories[0][name]", [""])[0]
                idnumber = params.get("categories[0][idnumber]", [""])[0]
                parent = params.get("categories[0][parent]", [""])[0]

                if idnumber == "edu_center_root":
                    response_data = [{"id": 100, "name": name}]
                elif idnumber == "edu_budget_finance" and parent == "100":
                    response_data = [{"id": 201, "name": name}]
                elif idnumber == "edu_staff_workload" and parent == "100":
                    response_data = [{"id": 202, "name": name}]
                elif idnumber == "edu_scholarships" and parent == "100":
                    response_data = [{"id": 203, "name": name}]
                elif idnumber == "edu_academic_reports" and parent == "100":
                    response_data = [{"id": 204, "name": name}]
                else:
                    response_data = []
            elif ws_function == "core_role_assign":
                response_data = {"status": "success"}
            else:
                response_data = {}

            # Return BytesIO response
            response = MagicMock()
            response.read.return_value = json.dumps(response_data).encode("utf-8")

            # Configure context manager support correctly
            response.__enter__.return_value = response
            return response

        mock_url_open.side_effect = side_effect
        yield mock_url_open


def urllib_parse_qs(data_str):
    """
    Simple custom query string parser to avoid relying on complex external components.
    """
    import urllib.parse
    return urllib.parse.parse_qs(data_str)


def test_configure_moodle_flow(mock_moodle_api):
    # Execute configuration script (simulating when root already exists)
    configure_moodle("http://mock-moodle/webservice/rest/server.php", "mock-token-abc")

    # Assert urlopen was called for category creations and role assignments
    assert mock_moodle_api.call_count > 0

    # Ensure all calls were sent to the correct mock server
    for call in mock_moodle_api.call_args_list:
        req = call[0][0]
        assert req.full_url.startswith("http://mock-moodle/webservice/rest/server.php")
        assert "wstoken=mock-token-abc" in req.full_url
        assert "moodlewsrestformat=json" in req.full_url
