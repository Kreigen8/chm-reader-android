#include <jni.h>
#include <cstdint>
#include <vector>
#include <string>
#include <cstring>
extern "C" {
#include "chm_lib.h"
}
static void fail(JNIEnv* e, const char* message) { e->ThrowNew(e->FindClass("java/io/IOException"), message); }
static chmFile* file(jlong h) { return reinterpret_cast<chmFile*>(h); }
extern "C" JNIEXPORT jlong JNICALL Java_org_chmreader_chm_NativeChm_openNative(JNIEnv* e,jobject,jstring path) {
    const char* p=e->GetStringUTFChars(path,nullptr); if(!p) return 0;
    chmFile* f=chm_open(p); e->ReleaseStringUTFChars(path,p);
    if(!f) fail(e,"Файл повреждён или не является CHM");
    return reinterpret_cast<jlong>(f);
}
extern "C" JNIEXPORT void JNICALL Java_org_chmreader_chm_NativeChm_closeNative(JNIEnv*,jobject,jlong h) { if(h) chm_close(file(h)); }
static void append(std::vector<uint8_t>& b,uint64_t n,int bytes) { for(int i=0;i<bytes;i++) b.push_back(static_cast<uint8_t>(n>>(i*8))); }
struct Listing { std::vector<uint8_t> bytes; bool overflow=false; };
static int enumerate(chmFile*,chmUnitInfo* u,void* context) {
    auto& l=*static_cast<Listing*>(context);
    const size_t n=strnlen(u->path,CHM_MAX_PATHLEN);
    if(l.bytes.size()+n+12>64*1024*1024) { l.overflow=true; return CHM_ENUMERATOR_FAILURE; }
    append(l.bytes,u->length,8); append(l.bytes,n,4);
    l.bytes.insert(l.bytes.end(),u->path,u->path+n);
    return CHM_ENUMERATOR_CONTINUE;
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_org_chmreader_chm_NativeChm_listNative(JNIEnv* e,jobject,jlong h) {
    Listing l;
    if(!h||!chm_enumerate(file(h),CHM_ENUMERATE_ALL,enumerate,&l)||l.overflow) { fail(e,"Не удалось прочитать каталог CHM"); return nullptr; }
    auto a=e->NewByteArray(static_cast<jsize>(l.bytes.size()));
    if(a&&!l.bytes.empty()) e->SetByteArrayRegion(a,0,static_cast<jsize>(l.bytes.size()),reinterpret_cast<const jbyte*>(l.bytes.data()));
    return a;
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_org_chmreader_chm_NativeChm_readNative(JNIEnv* e,jobject,jlong h,jbyteArray path,jlong offset,jint count) {
    if(!h||offset<0||count<0||count>1024*1024) { fail(e,"Недопустимый диапазон чтения"); return nullptr; }
    const jsize n=e->GetArrayLength(path);
    if(n<1||n>CHM_MAX_PATHLEN) { fail(e,"Недопустимый путь"); return nullptr; }
    std::string p(n,'\0'); e->GetByteArrayRegion(path,0,n,reinterpret_cast<jbyte*>(p.data()));
    if(p.find('\0')!=std::string::npos) { fail(e,"Недопустимый путь"); return nullptr; }
    chmUnitInfo u;
    if(chm_resolve_object(file(h),p.c_str(),&u)!=CHM_RESOLVE_SUCCESS) { fail(e,"Ресурс отсутствует в CHM"); return nullptr; }
    if(static_cast<uint64_t>(offset)>=u.length) return e->NewByteArray(0);
    std::vector<unsigned char> b(static_cast<size_t>(count));
    LONGINT64 got=chm_retrieve_object(file(h),&u,b.data(),offset,count);
    if(got<0||(count>0&&got==0)) { fail(e,"Ошибка распаковки CHM"); return nullptr; }
    auto a=e->NewByteArray(static_cast<jsize>(got));
    if(a&&got) e->SetByteArrayRegion(a,0,static_cast<jsize>(got),reinterpret_cast<jbyte*>(b.data()));
    return a;
}
