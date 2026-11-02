"""Verify bundled public catalog/provenance without contacting any academic portal."""
from pathlib import Path
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from urllib.parse import urlsplit

root = Path(__file__).resolve().parents[1]
assets = root / "app/src/main/assets"
catalog = json.loads((assets / "catalog/schools.json").read_text(encoding="utf-8"))
source = json.loads((assets / "catalog/source.json").read_text(encoding="utf-8"))
assert len(catalog) == source["ordinary"] + source["adult"] == 3196
assert len({s["id"] for s in catalog}) == len(catalog)
assert all(s["name"] and re.fullmatch(r"cn_\d{10}", s["id"]) for s in catalog)
index = json.loads((assets / "shiguang/index.json").read_text(encoding="utf-8"))
provenance = json.loads((assets / "shiguang/provenance.json").read_text(encoding="utf-8"))
assert provenance["commit"] == "c4a35feb061a3f6db2d6c122927083c067c61309"
assert len({s["id"] for s in index}) == len(index)
paths = set()
general = json.loads((assets / "shiguang/general.json").read_text(encoding="utf-8"))
for school in index + general:
    assert school["adapters"]
    for adapter in school["adapters"]:
        path = assets / "shiguang/scripts" / adapter["path"]
        assert path.resolve().is_relative_to((assets / "shiguang/scripts").resolve())
        assert adapter["url"].startswith(("https://", "http://")) or school in general
        assert adapter["sourceUrl"].startswith(f"{provenance['repository']}/blob/{provenance['commit']}/resources/")
        assert re.fullmatch(r"\d{4}-\d{2}-\d{2}T.*", adapter["updated"])
        assert hashlib.sha256(path.read_bytes()).hexdigest() == provenance["scripts"][adapter["path"]]
        paths.add(adapter["path"])
assert paths == set(provenance["scripts"])
wakeup = json.loads((assets / "academic/wakeup-provenance.json").read_text(encoding="utf-8"))
assert wakeup["commit"] == "43631a54cb70ceb2d983a4d646022d84352c9d85" and wakeup["license"] == "Apache-2.0"
for rel, entry in wakeup["files"].items():
    target = root / "app/src/main/java/com/kstudio/agenda/data/academic/wakeup" / rel
    assert target.resolve().is_relative_to((root / "app/src/main/java/com/kstudio/agenda/data/academic/wakeup").resolve())
    assert hashlib.sha256(target.read_bytes()).hexdigest() == entry["vendoredSha256"]
assert hashlib.sha256((assets / "academic/wakeup-schools.json").read_bytes()).hexdigest() == wakeup["directorySha256"]
assert "Apache License" in (assets / "licenses/wakeup-Apache-2.0.txt").read_text(encoding="utf-8")
assert "YZune" in (assets / "licenses/wakeup-NOTICE.txt").read_text(encoding="utf-8")
assert "Jonathan Hedley" in (assets / "licenses/jsoup-MIT.txt").read_text(encoding="utf-8")
assert "Apache License" in (assets / "licenses/gson-Apache-2.0.txt").read_text(encoding="utf-8")
network = ET.parse(root / "app/src/main/res/xml/network_security_config.xml")
assert network.find("base-config").get("cleartextTrafficPermitted") == "false"
hosts = {d.text for d in network.findall(".//domain")}
assert "127.0.0.1" not in hosts
debug_hosts = {d.text for d in ET.parse(root / "app/src/debug/res/xml/network_security_config.xml").findall(".//domain")}
assert debug_hosts == hosts | {"127.0.0.1"}
for row in index + json.loads((assets / "academic/wakeup-schools.json").read_text(encoding="utf-8")) + json.loads((assets / "academic/school-adapters.json").read_text(encoding="utf-8")):
    for adapter in row.get("adapters", [row]):
        url = urlsplit(adapter["url"])
        if url.scheme == "http":
            assert url.hostname in hosts
license_text = (assets / "licenses/shiguang-warehouse-MIT.txt").read_text(encoding="utf-8")
assert "MIT License" in license_text and "2025 星河欲转" in license_text
assert "Apache License" in (assets / "licenses/school-badges-Apache-2.0.txt").read_text(encoding="utf-8")
pinyin_source = json.loads((assets / "catalog/pinyin-source.json").read_text(encoding="utf-8"))
assert pinyin_source["version"] == "0.55.0" and pinyin_source["license"] == "MIT"
assert hashlib.sha256((assets / "catalog/pinyin.json").read_bytes()).hexdigest() == pinyin_source["sha256"]
assert hashlib.sha256((assets / "catalog/pinyin-overrides.json").read_bytes()).hexdigest() == pinyin_source["overridesSha256"]
assert "mozillazg" in (assets / "licenses/pypinyin-MIT.txt").read_text(encoding="utf-8")
extra = json.loads((assets / "academic/school-adapters.json").read_text(encoding="utf-8"))
extra_source = json.loads((assets / "academic/school-provenance.json").read_text(encoding="utf-8"))
assert extra_source["accountValidated"] is False
assert hashlib.sha256((assets / "academic/school-common.js").read_bytes()).hexdigest() == extra_source["commonSha256"]
evidence = {}
def add_evidence(entry):
    key = entry['repository'], entry['path'], entry.get('commit') or entry.get('revision')
    previous = evidence.get(key)
    # The same pinned source can be inspected in multiple provinces. A later
    # transient fetch failure must not erase an earlier verified byte hash.
    if previous and previous.get('sha256'):
        if entry.get('sha256'):
            assert previous['sha256'] == entry['sha256'], ('conflicting pinned bytes', key)
        else:
            return
    evidence[key] = entry

for research in ["single-school-research", "zhejiang-undergraduate-research"]:
    for e in json.loads((root / f"docs/{research}/source-evidence.json").read_text(encoding="utf-8")):
        add_evidence(e)
for path in (root / "docs/provincial-undergraduate-research").glob("*/source-evidence.json"):
    for e in json.loads(path.read_text(encoding="utf-8")):
        add_evidence(e)
ids = set()
system_labels = json.loads((assets/'academic/school-systems.json').read_text(encoding='utf-8'))
assert hashlib.sha256((assets/'academic/school-systems.json').read_bytes()).hexdigest() == extra_source['systemEvidence']['sha256']
assert len({r['name'] for r in system_labels}) == len(system_labels) == extra_source['systemEvidence']['schools']
for label in system_labels:
    assert any(r['name'] == label['name'] for r in catalog)
    assert label['classification'] == 'vendor_evidence_only_not_import_support' and label['accountValidated'] is False
    if label.get('officialPublicPage'):
        page=label['officialPublicPage']
        assert page['status']==200 and page['unauthenticated'] is True and len(page['sha256'])==64
        assert page['url'].startswith('https://') and page['url']==page['finalUrl']
    for original in label['sources']:
        repo = original['repository'].removeprefix('https://github.com/')
        raw = evidence[repo, original['path'], original['revision']]
        assert original['sha256'] == raw['sha256'] and original['sourceUrl'] == raw['sourceUrl']
for group in extra:
    assert any(s["name"] == group["name"] for s in catalog)
    for entry in group["adapters"]:
        assert entry["id"] not in ids
        ids.add(entry["id"])
        assert entry["license"] in ["MIT", "Apache-2.0", "LicenseRef-Dongfang-Permissive"] and len(entry["revision"]) == 40
        assert entry["url"].startswith(("https://", "http://")) and entry["licenseAsset"] in extra_source["licenses"]
        if entry["license"] == "LicenseRef-Dongfang-Permissive":
            assert entry["repository"] == "https://github.com/my832/dongfang-timetable"
        source_file = extra_source["files"][entry["path"]]
        if entry.get('implementationOriginal'):
            assert entry['licenseScope'] == 'KAgenda-original-implementation-only'
            assert entry['copiedUpstreamLines'] == source_file['copiedUpstreamLines'] == 0
            assert source_file['implementationOriginal'] is True
            assert source_file['implementationLicense'] == entry['license']
            assert source_file['referenceLicense'] == entry['referenceLicense']
            assert all(s.get('protocolReferenceOnly') is True for s in source_file['sources'])
            notice = (assets/'licenses'/entry['licenseAsset']).read_bytes()
            project_license = (root.parent/'LICENSE').read_bytes()
            assert project_license in notice
            assert extra_source['licenses'][entry['licenseAsset']]['projectLicenseSha256'] == hashlib.sha256(project_license).hexdigest()
        assert any(s["adapterId"] == entry["id"] and s["sourceUrl"] == entry["sourceUrl"] and s["updated"] == entry["updated"] for s in source_file["sources"])
for local, entry in extra_source["files"].items():
    path = assets / "shiguang/scripts" / local
    assert path.resolve().is_relative_to((assets / "shiguang/scripts/EXT").resolve())
    assert hashlib.sha256(path.read_bytes()).hexdigest() == entry["sha256"]
    if entry.get('communityProtocolReference'):
        reference = entry['communityProtocolReference']
        rel = reference['path'].removeprefix('resources/')
        assert reference['repository'] == provenance['repository'] and reference['revision'] == provenance['commit']
        assert reference['sourceUrl'] == provenance['files'][rel]['sourceUrl']
        assert reference['sha256'] == provenance['scripts'][rel]
    for original in entry["sources"] + entry.get("supportingSources", []):
        repo = original["repository"].removeprefix("https://github.com/")
        raw = evidence[repo, original["path"], original.get("revision") or original.get("commit")]
        assert original["sha256"] == raw["sha256"] and original["sourceUrl"] == raw["sourceUrl"]
for name, entry in extra_source["licenses"].items():
    assert hashlib.sha256((assets / "licenses" / name).read_bytes()).hexdigest() == entry["sha256"]
print(f"Verified {len(extra)} additional school groups, {len(ids)} ports, pinned source hashes, dates and original license notices.")
# Android launcher previews must contain samples on both old and new system versions.
android = "{http://schemas.android.com/apk/res/android}"
for kind in ("2x1", "2x2", "4x1", "4x2"):
    provider = ET.parse(root / f"app/src/main/res/xml/widget_info_{kind}.xml").getroot()
    assert provider.get(android + "previewImage") == f"@drawable/widget_preview_{kind}"
    assert provider.get(android + "previewLayout") == f"@layout/widget_preview_{kind}"
print(f"Verified {len(catalog)} official entries, {len(index)} adapter groups, {len(paths)} unchanged scripts, licenses and 4 legacy previews.")
