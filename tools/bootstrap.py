"""Fetch verified Android build tools. Run with Python 3 on Windows."""
import concurrent.futures, hashlib, json, os, pathlib, urllib.request, zipfile
import xml.etree.ElementTree as ET
ROOT = pathlib.Path(__file__).resolve().parents[1]
TOOLS = pathlib.Path(os.environ.get('CHM_TOOLS', str(pathlib.Path.home()/'chm-tools')))
SDK = pathlib.Path(os.environ.get('ANDROID_HOME', str(TOOLS / 'sdk')))
TOOLS.mkdir(parents=True, exist_ok=True)
def fetch(url, path, digest=None, algo='sha256'):
    if not path.exists():
        print('Downloading', path.name, flush=True)
        urllib.request.urlretrieve(url, path)
    if digest:
        h = hashlib.new(algo)
        with path.open('rb') as f:
            for b in iter(lambda: f.read(1024*1024), b''): h.update(b)
        if h.hexdigest().lower() != digest.lower(): raise RuntimeError('Checksum failed: '+str(path))
def extract(path, dest):
    dest.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path) as z:
        files=[pathlib.PurePosixPath(m.filename).parts for m in z.infolist() if not m.is_dir()]
        strip_root=bool(files) and all(len(parts)>1 for parts in files) and len({parts[0] for parts in files})==1
        for m in z.infolist():
            parts = pathlib.PurePosixPath(m.filename).parts[1 if strip_root else 0:]
            if not parts or m.is_dir(): continue
            p = dest.joinpath(*parts)
            if not p.resolve().is_relative_to(dest.resolve()): raise ValueError('zip traversal')
            p.parent.mkdir(parents=True, exist_ok=True)
            with z.open(m) as src, p.open('wb') as out:
                import shutil
                shutil.copyfileobj(src, out)
def sdk_package(name, dest):
    if (dest/'source.properties').exists(): return
    repo = ET.parse(ROOT/'repository.xml')
    p = next(p for p in repo.getroot().findall('remotePackage') if p.attrib['path']==name)
    a = next(a for a in p.findall('./archives/archive') if a.findtext('host-os') in (None,'windows'))
    url = a.findtext('./complete/url'); c = a.find('./complete/checksum'); archive = TOOLS/pathlib.PurePosixPath(url).name
    fetch('https://dl.google.com/android/repository/'+url,archive,c.text,c.attrib['type'])
    extract(archive,dest)
    print('Ready', name, flush=True)
def jdk():
    dest=TOOLS/'jdk'
    if (dest/'bin/java.exe').exists(): return
    req=urllib.request.Request('https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows',headers={'User-Agent':'CHM-Viewer-build'})
    p=json.load(urllib.request.urlopen(req))[0]['binary']['package']
    archive=TOOLS/p['name']; fetch(p['link'],archive,p['checksum']); extract(archive,dest)
    print('Ready JDK 17', flush=True)
jobs=[('platforms;android-37.0',SDK/'platforms/android-37.0'),('build-tools;37.0.0',SDK/'build-tools/37.0.0'),('ndk;28.2.13676358',SDK/'ndk/28.2.13676358'),('cmake;3.22.1',SDK/'cmake/3.22.1'),('cmdline-tools;latest',SDK/'cmdline-tools/latest')]
with concurrent.futures.ThreadPoolExecutor(max_workers=5) as ex:
    tasks=[ex.submit(sdk_package,*j) for j in jobs]+[ex.submit(jdk)]
    for t in tasks: t.result()
# The integer API field is required by the SDK local-package reader, even when
# Google's platform archive/package name includes the '.0' minor version.
props=SDK/'platforms/android-37.0/source.properties'
if props.exists():
    props.write_text(props.read_text().replace('AndroidVersion.ApiLevel=37.0','AndroidVersion.ApiLevel=37'),encoding='utf-8')
print('Accept SDK licences with Android Studio SDK Manager before the first build.',flush=True)
print('SDK:',SDK,flush=True)
