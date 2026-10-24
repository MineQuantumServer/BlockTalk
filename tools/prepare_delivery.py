"""Collect source/deliverables without any run directories, identities or credentials."""
from pathlib import Path
import hashlib,zipfile,shutil,json,xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
out=root/'outputs';out.mkdir(exist_ok=True)
prefixes=('.github/','src/','core/src/','third_party/ts3j/src/','third_party/opus/src/','docs/','licenses/','gradle/','tools/')
exact={'.gitattributes','.gitignore','LICENSE','README.md','THIRD_PARTY_NOTICES','build.gradle','settings.gradle','gradle.properties','gradle.lockfile','gradlew','gradlew.bat','core/build.gradle','core/gradle.lockfile'}
for project in ['ts3j','opus']:
    for name in ['build.gradle','gradle.lockfile','LICENSE','UPSTREAM.md','PATCHES.diff','SOURCE_SHA256SUMS','pom.xml','opus-1.6.1.tar.gz']:
        exact.add(f'third_party/{project}/{name}')
files=[]
for p in root.rglob('*'):
    if not p.is_file():continue
    name=p.relative_to(root).as_posix()
    if name in exact or any(name.startswith(prefix) for prefix in prefixes):
        if '__pycache__/' not in name and not name.endswith('.pyc'):files.append(p)
assert not any(p.name.endswith('.identity') or 'settings.json'==p.name for p in files)
with zipfile.ZipFile(out/'BlockTalk-source.zip','w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(files):z.write(p,'BlockTalk/'+p.relative_to(root).as_posix())
jar=root/'build/libs/blocktalk-0.1.0-alpha.1.jar';shutil.copy2(jar,out/jar.name)
for name in ['README.md','THIRD_PARTY_NOTICES','LICENSE']:shutil.copy2(root/name,out/name)
shutil.copytree(root/'docs',out/'docs',dirs_exist_ok=True)
results=[]
for path in ['core/build/test-results/test','third_party/ts3j/build/test-results/test','core/build/test-results/nativeTest','core/build/test-results/hardwareTest']:
    totals={key:0 for key in ['tests','failures','errors','skipped']}
    for xml in (root/path).glob('TEST-*.xml'):
        suite=ET.parse(xml).getroot()
        for key in totals:totals[key]+=int(suite.get(key,'0'))
    results.append({'suite':path,**totals})
(out/'test-results.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf-8')
manifest=[]
for p in sorted(out.rglob('*')):
    if p.is_file() and p.name!='SHA256SUMS.txt':manifest.append(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.relative_to(out).as_posix())
(out/'SHA256SUMS.txt').write_text('\n'.join(manifest)+'\n',encoding='utf-8')
print('Delivery:',len(files),'source files;',jar.stat().st_size,'JAR bytes')
print('Results:',results)
