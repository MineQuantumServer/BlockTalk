"""Run the built JAR under the resolved MDK runtime without source folders/project libraries.
This verifies packaging and nested resource loading; it is not a clean launcher installation.
Run gradlew prepareSmokeClientRun build before this tool.
"""
from pathlib import Path
import os,sys,subprocess,shutil
root=Path(__file__).resolve().parents[1]
run=root/'work/packaged-smoke';(run/'mods').mkdir(parents=True,exist_ok=True)
jar=root/'build/libs/blocktalk-0.1.0-alpha.1.jar';shutil.copy2(jar,run/'mods'/jar.name)
legacy=(root/'build/moddev/smokeClientLegacyClasspath.txt').read_text()
exclude=('core-0.1.0','ts3j-1.0.3','opus-1.6.1','bcprov-jdk18on','ini4j-','dnsjava-')
paths=[p for p in legacy.splitlines() if p and not any(x in p for x in exclude)]
game=root/'build/moddev/artifacts/neoforge-21.1.264.jar'
paths.append(str(game))
(run/'classpath.txt').write_text('\n'.join(paths))
vm=(root/'build/moddev/smokeClientRunVmArgs.txt').read_text().splitlines()
vm=[line if not line.startswith('-DlegacyClassPath.file=') else '-DlegacyClassPath.file='+str(run/'classpath.txt').replace('\\','/') for line in vm]
(run/'vmargs.txt').write_text('\n'.join(vm))
shutil.copy2(root/'build/moddev/smokeClientRunProgramArgs.txt',run/'args.txt')
env=os.environ.copy();env.pop('MOD_CLASSES',None);env.pop('FML_MOD_CLASSES',None)
java=Path(env['JAVA_HOME'])/'bin/java.exe'
result=subprocess.run([str(java),'-cp',os.pathsep.join(paths),'@'+str(run/'vmargs.txt'),'@'+str(run/'args.txt')],cwd=run,env=env)
print('PACKAGED_SMOKE_EXIT='+str(result.returncode));sys.exit(result.returncode)
