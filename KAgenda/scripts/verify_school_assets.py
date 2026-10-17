"""Verify bundled public catalog/provenance without contacting any academic portal."""
from pathlib import Path
import hashlib
import json
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
assets = root / "app/src/main/assets"
catalog = json.loads((assets / "catalog/schools.json").read_text(encoding="utf-8"))
source = json.loads((assets / "catalog/source.json").read_text(encoding="utf-8"))
assert len(catalog) == source["ordinary"] + source["adult"] == 3196
assert len({s["id"] for s in catalog}) == len(catalog)
assert all(s["name"] and re.fullmatch(r"cn_\d{10}", s["id"]) for s in catalog)
index = json.loads((assets / "shiguang/index.json").read_text(encoding="utf-8"))
provenance = json.loads((assets / "shiguang/provenance.json").read_text(encoding="utf-8"))
assert provenance["commit"] == "1dc0772566ba610f495906410734db4e7b8ef0c1"
assert len({s["id"] for s in index}) == len(index)
paths = set()
for school in index:
    assert school["adapters"]
    for adapter in school["adapters"]:
        path = assets / "shiguang/scripts" / adapter["path"]
        assert path.resolve().is_relative_to((assets / "shiguang/scripts").resolve())
        assert adapter["url"].startswith(("https://", "http://"))
        assert hashlib.sha256(path.read_bytes()).hexdigest() == provenance["scripts"][adapter["path"]]
        paths.add(adapter["path"])
assert paths == set(provenance["scripts"])
license_text = (assets / "licenses/shiguang-warehouse-MIT.txt").read_text(encoding="utf-8")
assert "MIT License" in license_text and "2025 星河欲转" in license_text
assert "Apache License" in (assets / "licenses/school-badges-Apache-2.0.txt").read_text(encoding="utf-8")
pinyin_source = json.loads((assets / "catalog/pinyin-source.json").read_text(encoding="utf-8"))
assert pinyin_source["version"] == "0.55.0" and pinyin_source["license"] == "MIT"
assert hashlib.sha256((assets / "catalog/pinyin.json").read_bytes()).hexdigest() == pinyin_source["sha256"]
assert hashlib.sha256((assets / "catalog/pinyin-overrides.json").read_bytes()).hexdigest() == pinyin_source["overridesSha256"]
assert "mozillazg" in (assets / "licenses/pypinyin-MIT.txt").read_text(encoding="utf-8")
# Android launcher previews must contain samples on both old and new system versions.
android = "{http://schemas.android.com/apk/res/android}"
for kind in ("2x1", "2x2", "4x1", "4x2"):
    provider = ET.parse(root / f"app/src/main/res/xml/widget_info_{kind}.xml").getroot()
    assert provider.get(android + "previewImage") == f"@drawable/widget_preview_{kind}"
    assert provider.get(android + "previewLayout") == f"@layout/widget_preview_{kind}"
print(f"Verified {len(catalog)} official entries, {len(index)} adapter groups, {len(paths)} unchanged scripts, licenses and 4 legacy previews.")
