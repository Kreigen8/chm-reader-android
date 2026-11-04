"""Verify private CHMs through the vendored CHMLib on Windows; export metadata for JVM tests."""
import ctypes as C, pathlib, struct, argparse, json
ROOT=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(); p.add_argument('--dll',default=str(ROOT/'private-tests/chm.dll')); p.add_argument('samples',nargs='+'); a=p.parse_args()
class Unit(C.Structure):
    _fields_=[('start',C.c_uint64),('length',C.c_uint64),('space',C.c_int),('flags',C.c_int),('path',C.c_char*513)]
lib=C.CDLL(a.dll)
lib.chm_open.argtypes=[C.c_char_p]; lib.chm_open.restype=C.c_void_p
lib.chm_close.argtypes=[C.c_void_p]
lib.chm_resolve_object.argtypes=[C.c_void_p,C.c_char_p,C.POINTER(Unit)]; lib.chm_resolve_object.restype=C.c_int
lib.chm_retrieve_object.argtypes=[C.c_void_p,C.POINTER(Unit),C.c_void_p,C.c_uint64,C.c_int64]; lib.chm_retrieve_object.restype=C.c_int64
Callback=C.CFUNCTYPE(C.c_int,C.c_void_p,C.POINTER(Unit),C.c_void_p)
lib.chm_enumerate.argtypes=[C.c_void_p,C.c_int,Callback,C.c_void_p]; lib.chm_enumerate.restype=C.c_int
for filename in a.samples:
    file=pathlib.Path(filename).resolve()
    with file.open('rb') as source: header_language=struct.unpack_from('<I',source.read(24),20)[0]
    h=lib.chm_open(str(file).encode())
    if not h: raise RuntimeError('CHMLib failed to open '+str(file))
    try:
        items=[]
        @Callback
        def collect(_,unit,__):
            u=unit.contents; items.append((bytes(u.path),u.length,u.space)); return 1
        assert lib.chm_enumerate(h,31,collect,None)
        def read(path,limit=32*1024*1024):
            u=Unit(); assert lib.chm_resolve_object(h,path,C.byref(u))==0,path
            if u.length>limit: raise ValueError('Large metadata: '+repr(path))
            out=bytearray(); offset=0
            while offset<u.length:
                b=C.create_string_buffer(min(65536,u.length-offset)); n=lib.chm_retrieve_object(h,C.byref(u),b,offset,len(b))
                assert n>0,(path,offset); out.extend(b.raw[:n]); offset+=n
            return bytes(out)
        output=ROOT/'private-tests'/file.stem; output.mkdir(parents=True,exist_ok=True)
        system=read(b'/#SYSTEM'); (output/'system.bin').write_bytes(system)
        (output/'header-language.txt').write_text(str(header_language))
        (output/'archive-name.txt').write_text(file.name,encoding='utf-8')
        paths=[i[0].decode('utf-8',errors='replace') for i in items]
        (output/'entries.txt').write_text('\n'.join(paths),encoding='utf-8')
        hhc=[i for i in items if i[0].lower().endswith(b'.hhc')]
        assert hhc,'No HTML contents in '+file.name
        for i,(path,length,space) in enumerate(hhc):
            (output/f'toc-{i}.bin').write_bytes(read(path)); (output/f'toc-{i}.path').write_text(path.decode('utf-8'),encoding='utf-8')
        image_count=0; html_count=0
        selected=[i for i in items if i[0].lower().endswith((b'.htm',b'.html'))][:150]
        for i,(path,length,space) in enumerate(selected):
            if length>8*1024*1024: continue
            (output/f'page-{i}.bin').write_bytes(read(path)); (output/f'page-{i}.path').write_text(path.decode('utf-8'),encoding='utf-8'); html_count+=1
        for path,length,space in items:
            if path.lower().endswith((b'.png',b'.jpg',b'.gif',b'.bmp')) and length>0:
                u=Unit(); assert lib.chm_resolve_object(h,path,C.byref(u))==0
                b=C.create_string_buffer(min(64,length)); assert lib.chm_retrieve_object(h,C.byref(u),b,0,len(b))>0; image_count+=1
                if image_count>=100: break
        result={'name':file.name,'size':file.stat().st_size,'entries':len(items),'hhc':len(hhc),'html_read':html_count,'images_read':image_count}
        print(json.dumps(result,ensure_ascii=False),flush=True)
        (output/'native-result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    finally: lib.chm_close(h)
