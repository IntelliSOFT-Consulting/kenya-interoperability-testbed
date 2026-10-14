#!/usr/bin/env python3
"""Generate mock-hie Client Registry stubs from the suite's fixtures.json.

dha-hie-suite/resources/fixtures.json is the single source of test identities:
the ITB test cases tell testers which numbers to search for, and this script
makes mock-hie answer for exactly those numbers. Run with --check to fail
(exit 1) when the committed mock files are out of date.

Usage: generate_fixtures.py <fixtures.json> <mock-hie dir> [--check]
"""
import argparse
import json
import re
import sys
from pathlib import Path

BASE_PATH = "/uat-middleware/api/v1"
ID_TYPES = ["National ID", "ClientRegistry ID", "Birth Notification", "Birth Certificate",
            "Alien ID", "Refugee ID", "Mandate Number"]


def slug(text):
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")


def client_record(c):
    dependants = []
    if c.get("withDependants"):
        dependants = [{
            "date_added": "2026-01-01",
            "relationship": "child",
            "total": 1,
            "result": [{
                "id": c["crId"] + "-D1", "first_name": "Test", "last_name": c["lastName"] + "-Dependant",
                "gender": "male", "date_of_birth": "2022-05-05",
                "identification_type": "Birth Certificate", "identification_number": "BC-MOCK-DEP-0001",
            }],
        }]
    return {
        "id": c["crId"], "resourceType": "Patient", "title": "",
        "first_name": c["firstName"], "middle_name": "Mock", "last_name": c["lastName"],
        "gender": c["gender"], "date_of_birth": c["dateOfBirth"], "place_of_birth": "Nairobi",
        "deceased_datetime": "", "civil_status": "Single", "citizenship": "Kenyan",
        "phone": "+254700000000", "kra_pin": "",
        "identification_type": c["type"], "identification_number": c["number"],
        "id_serial": "", "identification_residence": "",
        "country": "Kenya", "county": "Nairobi", "sub_county": "Westlands", "ward": "Parklands",
        "village_estate": "Test Estate", "city": "Nairobi", "building_house_no": "1",
        "postal_address": "", "zip_code": "", "province_state_country": "", "latitude": "", "longitude": "",
        "employment_type": "", "employer_name": "", "employer_pin": "", "domestic_worker_type": "",
        "gross_income": 0, "gross_income_currency": "KES",
        "learning_institution_code": "", "learning_institution_name": "", "grade_level": "",
        "admission_number": "", "admission_remand_number": "",
        "disability_category": "", "disability_subcategory": "", "disability_cause": "",
        "agent_id": "", "is_agent": 0, "preferred_primary_care_network": "",
        "meta": {"creationTime": "2026-01-01T00:00:00Z", "lastUpdated": "2026-01-01T00:00:00Z",
                 "source": "mock-hie", "versionId": "1"},
        "originSystem": {"record_id": c["crId"], "system": "mock-hie"},
        "other_identifications": [],
        "dependants": dependants,
        "unconfirmed_dependants": [],
    }


def validate(fixtures):
    clients = [fixtures["nationalId"]] + fixtures["alternates"]
    errors = []
    seen = set()
    for c in clients:
        if c["type"] not in ID_TYPES:
            errors.append(f"{c['number']}: type '{c['type']}' is not in the API enum")
        key = (c["type"], c["number"])
        if key in seen:
            errors.append(f"duplicate fixture {key}")
        seen.add(key)
        if c["number"] == fixtures["notFound"]["number"]:
            errors.append(f"{c['number']} collides with the reserved not-found number")
    if errors:
        sys.exit("invalid fixtures.json:\n  " + "\n  ".join(errors))
    return clients


def render(fixtures):
    """Return {relative path: file content} for every generated file."""
    files = {}
    stubs = []
    for c in validate(fixtures):
        body_file = f"patients/{slug(c['type'])}.json"
        files[f"__files/{body_file}"] = json.dumps(client_record(c), indent=2) + "\n"
        stubs.append({
            "name": f"patients: fixture {c['type']} {c['number']}",
            "priority": 5,
            "request": {
                "method": "GET",
                "urlPath": f"{BASE_PATH}/patients",
                "queryParameters": {
                    "identification_type": {"equalTo": c["type"]},
                    "identification_number": {"equalTo": c["number"]},
                },
            },
            "response": {"status": 200, "headers": {"Content-Type": "application/json"},
                         "bodyFileName": body_file},
        })
    files["mappings/patients-fixtures.json"] = json.dumps({"mappings": stubs}, indent=2) + "\n"
    return files


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("fixtures")
    parser.add_argument("mock_dir")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    files = render(json.loads(Path(args.fixtures).read_text()))
    mock_dir = Path(args.mock_dir)
    stale = [p for p, content in files.items()
             if not (mock_dir / p).exists() or (mock_dir / p).read_text() != content]
    if args.check:
        if stale:
            sys.exit("mock-hie fixtures are out of date; run scripts/check-fixtures.sh --write:\n  "
                     + "\n  ".join(stale))
        print(f"mock-hie fixtures up to date ({len(files)} files)")
        return
    for p in stale:
        (mock_dir / p).parent.mkdir(parents=True, exist_ok=True)
        (mock_dir / p).write_text(files[p])
    print(f"wrote {len(stale)} of {len(files)} files")


if __name__ == "__main__":
    main()
