from pathlib import Path
import urllib.request,json,hashlib,tarfile,subprocess,sys,os
base=Path('work/native-build').resolve();base.mkdir(parents=True,exist_ok=True)
def fetch(url,file,sha=None):
    if not file.exists(): subprocess.run(['curl.exe','--fail','--location','--output',str(file),url],check=True)
    actual=hashlib.sha256(file.read_bytes()).hexdigest()
    if sha and actual!=sha: raise ValueError('Checksum mismatch '+file.name)
    print(file.name,actual,flush=True)
meta={'tarball':'https://ziglang.org/download/0.14.1/zig-x86_64-windows-0.14.1.zip','shasum':'554f5378228923ffd558eac35e21af020c73789d87afeabf4bfd16f2e6feed2c'}
zigzip=base/'zig.zip';fetch(meta['tarball'],zigzip,meta['shasum'])
import zipfile
with zipfile.ZipFile(zigzip) as z: z.extractall(base)
zig=next(base.glob('zig-*windows*/zig.exe'))
subprocess.run([sys.executable,'-m','pip','install','--target',str(base/'tools'),'cmake==3.31.6','ninja==1.11.1.4'],check=True)
src=Path('third_party/opus/opus-1.6.1.tar.gz').resolve()
fetch('https://downloads.xiph.org/releases/opus/opus-1.6.1.tar.gz',src,'6ffcb593207be92584df15b32466ed64bbec99109f007c82205f0194572411a1')
with tarfile.open(src) as t: t.extractall(base,filter='data')
cc=base/'zig-cc.bat';cc.write_text('@echo off\n"'+str(zig)+'" cc %*\n')
cmake=base/'tools/cmake/data/bin/cmake.exe';ninja=base/'tools/ninja/data/bin/ninja.exe'
if not ninja.exists(): ninja=next((base/'tools').rglob('ninja.exe'))
args=[str(cmake),'-S',str(base/'opus-1.6.1'),'-B',str(base/'build'),'-G','Ninja','-DCMAKE_BUILD_TYPE=Release','-DCMAKE_C_COMPILER='+str(cc),'-DCMAKE_MAKE_PROGRAM='+str(ninja),'-DBUILD_SHARED_LIBS=ON','-DOPUS_BUILD_TESTING=OFF','-DOPUS_BUILD_PROGRAMS=OFF','-DOPUS_DRED=OFF','-DOPUS_OSCE=OFF','-DOPUS_DEEP_PLC=OFF']
subprocess.run(args,check=True)
subprocess.run([str(cmake),'--build',str(base/'build'),'--parallel','4'],check=True)
print(list((base/'build').rglob('*.dll')),flush=True)
print('Review the resulting DLL hash before replacing the vendored DLL and NativeLoader hash. No runtime downloads occur.',flush=True)
