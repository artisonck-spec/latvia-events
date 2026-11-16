"""Checks data/events.json before it reaches the phones. Exits with an error message if anything is wrong.
Usage: python3 tools/validate_events.py [data/events.json]"""
import json, re, sys
from datetime import date

path = sys.argv[1] if len(sys.argv) > 1 else 'data/events.json'
CATS = {'cars', 'moto', 'atv', 'festival', 'market', 'exhibition', 'outdoor', 'orienteering', 'tips'}
errors = []
try:
    data = json.load(open(path, encoding='utf-8'))
except Exception as e:
    sys.exit(f'{path} is not valid JSON: {e}')

events = data.get('events')
if not isinstance(events, list) or not events:
    sys.exit('"events" must be a non-empty list')

seen = set()
day = re.compile(r'^\d{4}-\d{2}-\d{2}$')
for i, e in enumerate(events):
    tag = f"#{i} {e.get('id', '?')}"
    for k in ('id', 'title', 'category', 'place', 'town', 'lat', 'lon', 'price', 'url', 'desc'):
        if e.get(k) in (None, ''):
            errors.append(f'{tag}: missing "{k}"')
    if e.get('id') in seen:
        errors.append(f'{tag}: duplicate id')
    seen.add(e.get('id'))
    if e.get('category') not in CATS:
        errors.append(f'{tag}: unknown category {e.get("category")!r} (use one of {sorted(CATS)})')
    if not e.get('always'):
        for k in ('start', 'end'):
            v = e.get(k, '')
            if not day.match(v or ''):
                errors.append(f'{tag}: "{k}" must be YYYY-MM-DD')
            else:
                try: date.fromisoformat(v)
                except ValueError: errors.append(f'{tag}: "{k}" is not a real date')
        if day.match(e.get('start') or '') and day.match(e.get('end') or '') and e['end'] < e['start']:
            errors.append(f'{tag}: end is before start')
        for d in e.get('dates', []) or []:
            if not day.match(d): errors.append(f'{tag}: bad date in "dates": {d}')
    try:
        lat, lon = float(e.get('lat')), float(e.get('lon'))
        if not (55.6 <= lat <= 58.2 and 20.8 <= lon <= 28.4):
            errors.append(f'{tag}: coordinates {lat},{lon} are outside Latvia')
    except (TypeError, ValueError):
        errors.append(f'{tag}: lat/lon must be numbers')
    for k in ('url', 'source', 'image'):
        v = e.get(k)
        if v and not str(v).startswith('https://'):
            errors.append(f'{tag}: "{k}" must start with https://')

if errors:
    print('\n'.join(errors)); sys.exit(f'{len(errors)} problem(s) in {path}')
print(f'{path}: {len(events)} events OK')
