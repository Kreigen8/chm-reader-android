"""Create a persistent local installation key. Secrets are excluded from source archives."""
import pathlib, os, secrets, subprocess, tempfile
root=pathlib.Path(__file__).resolve().parents[1]
props=root/'signing.properties'
if props.exists():
    print('Existing signing configuration retained')
    raise SystemExit()
keydir=pathlib.Path.home()/'.android'/'chm-reader-signing'; keydir.mkdir(parents=True,exist_ok=True)
key=keydir/'release.jks'
if key.exists(): raise RuntimeError('Key exists; recover its signing.properties rather than replacing it')
password=secrets.token_urlsafe(36)
jdk=pathlib.Path(os.environ.get('JAVA_HOME',str(pathlib.Path.home()/'chm-tools/jdk')))
with tempfile.TemporaryDirectory(prefix='chm-signing-') as tmp:
    secret=pathlib.Path(tmp)/'password'; secret.write_text(password)
    subprocess.run([str(jdk/'bin/keytool.exe'),'-genkeypair','-keystore',str(key),'-storetype','JKS','-alias','chm-reader','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=CHM Reader, O=CHM Reader, C=RU','-storepass:file',str(secret),'-keypass:file',str(secret)],check=True,capture_output=True)
props.write_text(f'storeFile={key.as_posix()}\nstorePassword={password}\nkeyAlias=chm-reader\nkeyPassword={password}\n',encoding='utf-8')
print('Local release signing key created; keep signing.properties and the private key for future APK updates')
