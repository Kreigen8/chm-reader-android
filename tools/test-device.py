"""Install test APKs and test private CHMs. Files are streamed to the debug app, never packaged."""
import argparse, pathlib, subprocess, os
ROOT=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(); p.add_argument('--adb',default=os.environ.get('ADB','adb')); p.add_argument('--serial'); p.add_argument('samples',nargs='+'); a=p.parse_args()
adb=[a.adb]+(['-s',a.serial] if a.serial else [])
def run(*args,**kwargs): return subprocess.run(adb+list(args),check=True,**kwargs)
run('install','-r',str(ROOT/'app/build/outputs/apk/debug/app-debug.apk'))
run('install','-r',str(ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
run('shell','run-as','org.chmreader','mkdir','-p','files/test-input')
for item in a.samples:
    sample=pathlib.Path(item).resolve()
    if not sample.is_file(): raise FileNotFoundError(sample)
    if any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-' for c in sample.name): raise ValueError('Use an ASCII sample filename')
    print('Streaming',sample.name,sample.stat().st_size,'bytes',flush=True)
    with sample.open('rb') as f:
        run('exec-in','run-as','org.chmreader','sh','-c',f'cat > files/test-input/{sample.name}',stdin=f)
    result=run('shell','am','instrument','-w','-r','-e','sample',sample.name,'org.chmreader.test/androidx.test.runner.AndroidJUnitRunner',capture_output=True,text=True,encoding='utf-8')
    print(result.stdout,flush=True)
    (ROOT/f'test-device-{sample.stem}.log').write_text(result.stdout,encoding='utf-8')
    if 'FAILURES' in result.stdout or 'FAILURE' in result.stdout or 'shortMsg=' in result.stdout: raise RuntimeError('Device tests failed')
run('logcat','-d','-s','CHM_TEST:I','AndroidRuntime:E')
