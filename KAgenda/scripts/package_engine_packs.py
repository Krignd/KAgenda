"""Prepare local versioned engine artifacts. Does not upload, install or change versions.

Build each ABI with -Pkagenda.engineAbi=<abi> before running this script.
The selected output must be deployed alongside the matching base APK, not an old monolithic release.
"""
from pathlib import Path
import argparse,hashlib,json,re,shutil,zipfile

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--variant',choices=['debug','release'],required=True)
parser.add_argument('--abi',choices=['universal','arm64-v8a','armeabi-v7a','x86','x86_64'],required=True)
parser.add_argument('--output',type=Path)
args=parser.parse_args()
gradle=(root/'app/build.gradle.kts').read_text(encoding='utf-8-sig')
version=int(re.search(r'versionCode\s*=\s*(\d+)',gradle).group(1))
out=(args.output or root/'app/build/engine-packs'/args.variant/str(version)).resolve()
assert out.is_relative_to(root.resolve()),'Output must stay in this project'
out.mkdir(parents=True,exist_ok=True)
manifest=out/'manifest.json'
data=json.loads(manifest.read_text(encoding='utf-8')) if manifest.exists() else dict(schema=1,packageName='com.kstudio.agenda',versionCode=version,engines={})
assert data['versionCode']==version and data['packageName']=='com.kstudio.agenda'
# Do not combine ABI packages from different source snapshots under the same version.
fingerprint=hashlib.sha256()
inputs=[root/'settings.gradle.kts',root/'build.gradle.kts',root/'gradle/libs.versions.toml']
for module in ['app','engine-ocr','engine-pdf','engine-ai']:
    inputs.append(root/module/'build.gradle.kts')
    inputs.extend(p for p in (root/module/'src/main').rglob('*') if p.is_file())
for p in sorted(inputs):
    fingerprint.update(p.relative_to(root).as_posix().encode());fingerprint.update(hashlib.sha256(p.read_bytes()).digest())
build_id=fingerprint.hexdigest()
if data.get('buildFingerprint')!=build_id:
    data=dict(schema=1,packageName='com.kstudio.agenda',versionCode=version,engines={},buildFingerprint=build_id)
sources=[]
if args.abi=='universal':
    assert args.variant=='release'
    staged=root/f'app/build/engine-release-staged/{version}'
    for name in ['ocr','pdf','ai']:
        abis=['any'] if name=='pdf' else ['arm64-v8a','x86_64'] if name=='ai' else ['arm64-v8a','armeabi-v7a','x86_64','x86']
        for abi in abis:sources.append((name,abi,staged/f'engine-{name}-{version}-{abi}.apk'))
else:
    for name in ['ocr','pdf','ai']:
        directory=root/f'engine-{name}/build/outputs/apk'/args.variant
        meta=json.loads((directory/'output-metadata.json').read_text(encoding='utf-8'))
        elements=meta['elements'];assert len(elements)==1
        source=directory/elements[0]['outputFile']
        sources.append((name,'any' if name=='pdf' else args.abi,source))
for name,abi,source in sources:
    with zipfile.ZipFile(source) as archive:
        receipt=json.loads(archive.read(f'assets/engine-receipts/{name}.json'))
        assert receipt==dict(engine=name,split='engine_'+name)
        abis={p.split('/')[1] for p in archive.namelist() if p.startswith('lib/')}
        assert not abis or abis=={abi},(name,abis,abi)
    filename=f'engine-{name}-{version}-{abi}.apk'
    target=out/filename;shutil.copyfile(source,target)
    item=dict(name=filename,bytes=target.stat().st_size,sha256=hashlib.sha256(target.read_bytes()).hexdigest())
    data['engines'].setdefault(name,dict(abis={}))['abis'][abi]=[item]
    print(name,abi,item['bytes'],item['sha256'])
base=root/f'app/build/outputs/apk/{args.variant}/app-{args.variant}.apk'
with zipfile.ZipFile(base) as archive:
    forbidden=[n for n in archive.namelist() if 'mlkit-google-ocr-models' in n or n.endswith('/libkagenda_ai.so') or n.startswith(('org/bouncycastle/','assets/com/tom_roush/')) or n.endswith('/libmlkit_google_ocr_pipeline.so')]
    assert not forbidden,forbidden[:10]
data.setdefault('baseApkSha256',{})[args.abi]=hashlib.sha256(base.read_bytes()).hexdigest()
shutil.copyfile(base,out/f'KAgenda-{version}-{args.abi}-base.apk')
manifest.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Base APK',base.stat().st_size,'bytes; optional engine payload absent. Local manifest:',manifest)
