from pathlib import Path
import json,re
root=Path(__file__).resolve().parents[1]
langs=[json.loads((root/f'src/main/resources/assets/blocktalk/lang/{l}.json').read_text(encoding='utf-8')) for l in ['en_us','zh_cn']]
assert langs[0].keys()==langs[1].keys(),'Language key sets differ'
required=set()
for p in (root/'src/main/java').rglob('*.java'):
    for key in re.findall(r'(?:tr|button|field|toggle|slider|super)\("([a-z][a-z0-9_.]*)"',p.read_text(encoding='utf-8')):
        if not key.endswith('.'):
            required.add('blocktalk.'+key)
for l in langs:
    assert required<=l.keys(), 'Missing translations: '+str(required-l.keys())
print(f'Localization PASS: {len(langs[0])} matching keys; {len(required)} static UI keys checked')
