"""Generate offline pronunciation data: pip install pypinyin==0.55.0."""
from pathlib import Path
import hashlib
import importlib.metadata
import json
import re
import shutil
import sys

root = Path(__file__).resolve().parents[1]
local = root / "app/build/external/python"
if local.exists():
    sys.path.insert(0, str(local))
from pypinyin import Style, lazy_pinyin, pinyin_dict

assert importlib.metadata.version("pypinyin") == "0.55.0"
assets = root / "app/src/main/assets"
catalog = json.loads((assets / "catalog/schools.json").read_text(encoding="utf-8"))
warehouse = json.loads((assets / "shiguang/index.json").read_text(encoding="utf-8"))
names = sorted({x["name"] for x in catalog + warehouse})

def syllables(text):
    return [re.sub(r"[^a-z0-9]", "", part.lower()) for part in lazy_pinyin(text, style=Style.NORMAL, errors=lambda text: list(text))]

# 单字兜底让用户自己添加的名称也能索引；词组读音优先使用完整校名的上下文。
characters = {chr(code): syllables(chr(code))[0] for code in sorted(pinyin_dict.pinyin_dict)}
overrides = json.loads((assets / "catalog/pinyin-overrides.json").read_text(encoding="utf-8"))["phrases"]
readings = {name: syllables(name) for name in names}
for name, parts in readings.items():
    for phrase, pronunciation in overrides.items():
        if name.startswith(phrase):
            parts[:len(phrase)] = pronunciation
prefixes = {}
for name, parts in readings.items():
    if len(parts) != len(name):
        continue
    for size in range(1, min(6, len(name)) + 1):
        prefix = name[:size]
        if parts[:size] != [characters.get(char, char.lower()) for char in prefix]:
            prefixes[prefix] = parts[:size]
payload = {"names": readings, "characters": characters, "prefixes": prefixes}
target = assets / "catalog/pinyin.json"
target.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
distribution = importlib.metadata.distribution("pypinyin")
license_file = next(distribution.locate_file(file) for file in distribution.files if str(file).endswith("licenses/LICENSE.txt"))
shutil.copyfile(license_file, assets / "licenses/pypinyin-MIT.txt")
(assets / "catalog/pinyin-source.json").write_text(json.dumps({
    "generator": "pypinyin", "version": "0.55.0", "repository": "https://github.com/mozillazg/python-pinyin",
    "license": "MIT", "scope": "offline school spelling and custom-name fallback",
    "names": len(names), "characters": len(characters), "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
    "overridesSha256": hashlib.sha256((assets / "catalog/pinyin-overrides.json").read_bytes()).hexdigest(),
}, indent=2), encoding="utf-8", newline="\n")
print(f"Generated {len(names)} names, {len(characters)} fallback characters and {len(prefixes)} contextual prefixes.")
