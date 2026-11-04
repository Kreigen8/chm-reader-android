"""Package rebuildable source without imported manuals, caches, keys or binaries."""
import pathlib, os, zipfile, hashlib, json, re, xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parents[1]
build=(root/'app/build.gradle').read_text(encoding='utf-8')
version=re.search(r"versionName\s*=\s*'([^']+)'",build).group(1)
version_code=int(re.search(r'versionCode\s*=\s*(\d+)',build).group(1))
dest=root/'dist'; dest.mkdir(exist_ok=True)
archive=dest/f'CHM-Reader-{version}-source.zip'
folders={'app','core','gradle','licenses','third_party','tools','.impeccable'}
files={'LICENSE','README.md','TESTING.md','RELEASE_NOTES.md','PRODUCT.md','DESIGN.md','settings.gradle','build.gradle','gradle.properties','gradlew','gradlew.bat','.gitignore','build-apk.ps1','repository.xml'}
excluded={'.git','.gradle','.kotlin','.cxx','build','dist','private-tests','__pycache__'}
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for current,dirs,names in os.walk(root):
        rel=pathlib.Path(current).relative_to(root)
        dirs[:]=[d for d in dirs if d not in excluded and (rel.parts or d in folders)]
        for name in sorted(names):
            p=pathlib.Path(current)/name
            if p.is_symlink() or (not rel.parts and name not in files): continue
            if p.suffix.lower() in {'.jks','.keystore','.dll','.pdb','.apk','.pyc'} or name=='signing.properties': continue
            z.write(p,'chm-reader-android/'+p.relative_to(root).as_posix())
apk=dest/f'CHM-Reader-{version}.apk'
suites=[ET.parse(p).getroot() for p in (root/'core/build/test-results/test').glob('TEST-*.xml')]
counts={key:sum(int(s.attrib.get(key,0)) for s in suites) for key in ['tests','failures','errors','skipped']}
manifest={'version':version,'versionCode':version_code,'minSdk':26,'targetSdk':37,'abis':['arm64-v8a','armeabi-v7a','x86_64'],'deviceUiVerified':False,'tests':{'core':counts['tests'],'failed':counts['failures']+counts['errors'],'skipped':counts['skipped'],'lintErrors':int(re.search(r'(\d+) errors,',(root/'app/build/reports/lint-results-debug.txt').read_text(encoding='utf-8')).group(1))},'artifacts':{p.name:{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in [apk,archive]}}
(dest/'build-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
update={'packageName':'org.chmreader','versionCode':version_code,'versionName':version,'apkUrl':f'https://github.com/Kreigen8/chm-reader-android/releases/download/v{version}/{apk.name}','sha256':manifest['artifacts'][apk.name]['sha256'],'size':apk.stat().st_size,'notes':(root/'RELEASE_NOTES.md').read_text(encoding='utf-8')[:12000]}
(dest/'update.json').write_text(json.dumps(update,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(dest/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in [apk,archive,dest/'update.json']),encoding='utf-8')
with zipfile.ZipFile(archive) as z:
    assert not any('/private-tests/' in n or n.endswith(('.chm','.jks','.keystore')) or n.endswith('signing.properties') for n in z.namelist())
    assert any(n.endswith('third_party/chmlib/src/lzx.c') for n in z.namelist())
print(json.dumps(manifest,ensure_ascii=False,indent=2))
