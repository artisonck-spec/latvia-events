"""Adds a picture to each event that has none, using the og:image / twitter:image of the event's own page.
Runs on GitHub (which can reach the web). Usage: python3 tools/fetch_images.py data/events.json"""
import json, re, sys, urllib.request, html as H

path = sys.argv[1]
data = json.load(open(path, encoding='utf-8'))
UA = {'User-Agent': 'Mozilla/5.0 (Linux; Android 14) LatviaEventsBot/1.0 (+https://github.com/artisonck-spec/latvia-events)'}
PAT = [r'<meta[^>]+property=["\']og:image(?::secure_url)?["\'][^>]+content=["\']([^"\']+)',
       r'<meta[^>]+content=["\']([^"\']+)["\'][^>]+property=["\']og:image',
       r'<meta[^>]+name=["\']twitter:image(?::src)?["\'][^>]+content=["\']([^"\']+)',
       r'<meta[^>]+content=["\']([^"\']+)["\'][^>]+name=["\']twitter:image']
SKIP = re.compile(r'(logo|favicon|default|placeholder|share-default)', re.I)

def find_image(url):
    try:
        req = urllib.request.Request(url, headers=UA)
        with urllib.request.urlopen(req, timeout=20) as r:
            if 'html' not in (r.headers.get('content-type') or ''): return None
            page = r.read(600_000).decode('utf-8', 'ignore')
    except Exception as ex:
        print('  cannot open', url, '-', ex); return None
    for p in PAT:
        m = re.search(p, page, re.I)
        if m:
            img = H.unescape(m.group(1)).strip()
            if img.startswith('//'): img = 'https:' + img
            if img.startswith('/'):
                img = re.match(r'https?://[^/]+', url).group(0) + img
            if img.startswith('http://'): img = 'https://' + img[7:]
            if img.startswith('https://') and not SKIP.search(img): return img
    return None

changed = 0
for e in data['events']:
    if e.get('image') or e.get('image_checked'): continue
    print('looking for a picture:', e['id'])
    for u in [e.get('url'), e.get('source')]:
        if u and u.startswith('https://'):
            img = find_image(u)
            if img:
                e['image'] = img; print('  found', img); break
    e['image_checked'] = True; changed += 1

if changed:
    json.dump(data, open(path, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
    open(path, 'a').write('\n')
print('checked', changed, 'event(s)')
