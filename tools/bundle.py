"""Put the current event list inside the page, so the app (and the preview) work offline on first start.
Usage: python3 tools/bundle.py <in.html> <events.json> <out.html>"""
import json, sys
src, data, out = sys.argv[1:4]
html = open(src, encoding='utf-8').read()
events = json.load(open(data, encoding='utf-8'))
marker = '/*BUNDLED_EVENTS*/null'
assert marker in html, 'marker missing'
html = html.replace(marker, json.dumps(events, ensure_ascii=False).replace('</', '<\\/'))
open(out, 'w', encoding='utf-8').write(html)
print('bundled', len(events['events']), 'events into', out)
