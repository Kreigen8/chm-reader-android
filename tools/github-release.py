"""Publish this project's source and verified APK through the saved Git credential.

Credentials stay in memory; signing keys and CHM inputs are never uploaded.
"""
import argparse, hashlib, json, os, pathlib, subprocess, urllib.error, urllib.parse, urllib.request
ROOT=pathlib.Path(__file__).resolve().parents[1]
REPOSITORY='Kreigen8/chm-reader-android'
OWNER,NAME=REPOSITORY.split('/')
parser=argparse.ArgumentParser(); parser.add_argument('action',choices=['status','create-repo','publish']); args=parser.parse_args()
env=os.environ.copy(); env['GIT_TERMINAL_PROMPT']='0'; env['GCM_INTERACTIVE']='Never'
credential=subprocess.run(['git','credential','fill'],input=f'protocol=https\nhost=github.com\nusername={OWNER}\n\n',text=True,capture_output=True,env=env,check=True)
fields=dict(line.split('=',1) for line in credential.stdout.splitlines() if '=' in line)
token=fields.get('password')
if not token: raise RuntimeError('Saved GitHub credential is unavailable')
def api(path,method='GET',data=None,content_type='application/json',allow404=False):
    url=path if path.startswith('https://') else 'https://api.github.com'+path
    if not url.startswith(('https://api.github.com/','https://uploads.github.com/')): raise ValueError('Invalid GitHub API host')
    body=json.dumps(data,ensure_ascii=False).encode() if data is not None and not isinstance(data,bytes) else data
    req=urllib.request.Request(url,data=body,method=method,headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','User-Agent':'CHM-Reader-release','Content-Type':content_type})
    try:
        with urllib.request.urlopen(req,timeout=120) as response:
            payload=response.read(); return json.loads(payload) if payload else None
    except urllib.error.HTTPError as e:
        if e.code==404 and allow404: return None
        try: message=json.loads(e.read()).get('message','Request failed')
        except Exception: message='Request failed'
        raise RuntimeError(f'GitHub {method} returned {e.code}: {message}') from None
profile=api('/user')
if profile['login'].lower()!=OWNER.lower(): raise RuntimeError('Saved Git account differs from the selected GitHub account')
repo=api('/repos/'+REPOSITORY,allow404=True)
if args.action=='status':
    print(json.dumps({'account':profile['login'],'repository':REPOSITORY,'exists':repo is not None,'private':repo.get('private') if repo else None}))
    raise SystemExit()
if repo is None:
    repo=api('/user/repos','POST',{'name':NAME,'description':'Free Android CHM reader: nested contents, original HTML, offline search, swipe navigation and manual GitHub updates.','private':False,'auto_init':False})
if repo['private']: raise RuntimeError('This updater is configured for public releases; repository is private')
if args.action=='create-repo':
    print(repo['html_url']); raise SystemExit()
manifest=json.loads((ROOT/'dist/build-manifest.json').read_text(encoding='utf-8'))
version=manifest['version']; tag='v'+version
apk=ROOT/'dist'/f'CHM-Reader-{version}.apk'; source=ROOT/'dist'/f'CHM-Reader-{version}-source.zip'
for file in (apk,source):
    assert hashlib.sha256(file.read_bytes()).hexdigest()==manifest['artifacts'][file.name]['sha256'],'Artifact hash mismatch'
update=json.loads((ROOT/'dist/update.json').read_text(encoding='utf-8'))
assert update['sha256']==manifest['artifacts'][apk.name]['sha256'] and update['versionName']==version
sha=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
release=api('/repos/'+REPOSITORY+'/releases/tags/'+tag,allow404=True)
if release and not release['draft']: raise RuntimeError('Published release already exists; do not overwrite it')
if release is None:
    notes=(ROOT/'RELEASE_NOTES.md').read_text(encoding='utf-8')
    release=api('/repos/'+REPOSITORY+'/releases','POST',{'tag_name':tag,'target_commitish':sha,'name':'CHM Reader '+version,'body':notes,'draft':True,'prerelease':False})
base=release['upload_url'].split('{')[0]
assets=[apk,source,ROOT/'dist/update.json',ROOT/'dist/SHA256SUMS']
existing={a['name']:a for a in release['assets']}
for file in assets:
    if file.name in existing:
        asset=existing[file.name]
        if asset.get('digest')=='sha256:'+hashlib.sha256(file.read_bytes()).hexdigest(): continue
        api('/repos/'+REPOSITORY+'/releases/assets/'+str(asset['id']),'DELETE')
    content='application/vnd.android.package-archive' if file.suffix=='.apk' else 'application/zip' if file.suffix=='.zip' else 'application/json' if file.suffix=='.json' else 'text/plain'
    asset=api(base+'?name='+urllib.parse.quote(file.name),'POST',file.read_bytes(),content)
    if asset.get('digest') and asset['digest']!='sha256:'+hashlib.sha256(file.read_bytes()).hexdigest(): raise RuntimeError('Uploaded asset checksum mismatch')
    print('Uploaded',file.name,flush=True)
published=api('/repos/'+REPOSITORY+'/releases/'+str(release['id']),'PATCH',{'draft':False,'make_latest':'true'})
print(published['html_url'])
