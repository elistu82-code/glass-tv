#!/usr/bin/env python3
"""Bounded measurements of our own launcher; never changes the HOME helper or system packages."""
import argparse, json, re, subprocess, time
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--device', required=True)
p.add_argument('--output', required=True)
p.add_argument('--cycles', type=int, default=3)
p.add_argument('--allow-launcher-stop', action='store_true', help='Only after explicit approval to stop Glass for cold-start measurements')
p.add_argument('--external', default='org.jellyfin.androidtv/.ui.startup.StartupActivity')
a = p.parse_args()
if not a.allow_launcher_stop:
    p.error('This legacy probe stops Glass TV. Explicit approval plus --allow-launcher-stop required; use measure_release.py for non-terminating measurements.')
assert re.fullmatch(r'[A-Za-z0-9_.:-]+', a.device)
assert re.fullmatch(r'[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+', a.external)
assert 1 <= a.cycles <= 5
out = Path(a.output); out.mkdir(parents=True, exist_ok=True)
adb = ['/usr/bin/adb', '-s', a.device]
def run(*args):
    return subprocess.run(adb + list(args), check=True, capture_output=True, timeout=120 if args[0]=='shell' and args[1].startswith('for ') else 35).stdout.decode(errors='replace')
def foreground():
    return next((l.strip() for l in run('shell','dumpsys','activity','activities').splitlines() if 'mResumedActivity:' in l), '')
def external():
    run('shell','input','keyevent','KEYCODE_WAKEUP')
    run('shell','am','start','-W','-n',a.external)
def dock_observed(start):
    # UIAutomator itself waits for idle; this is an upper bound, never precise FDTT.
    run('shell','uiautomator','dump','/data/local/tmp/glass-benchmark.xml')
    xml = run('exec-out','cat','/data/local/tmp/glass-benchmark.xml')
    assert 'de.glasslauncher.tv' in xml and 'Kalender öffnen' in xml
    return round((time.monotonic()-start)*1000)
run('shell','input','keyevent','KEYCODE_WAKEUP','KEYCODE_HOME')
results = {'cold':[], 'return':[], 'home_helper':[]}
(out/'accessibility-before.txt').write_text(run('shell','settings','get','secure','enabled_accessibility_services'))
for i in range(a.cycles):
    external()  # Prevent the unchanged HOME helper from racing our controlled cold start.
    run('shell','am','force-stop','de.glasslauncher.tv')
    device_start=int(float(run('shell','cat','/proc/uptime').split()[0])*1000)
    start=time.monotonic(); report=run('shell','am','start','-W','-n','de.glasslauncher.tv/.MainActivity')
    (out/f'cold-{i}.txt').write_text(report)
    values={k:int(v) for k,v in re.findall(r'(TotalTime|WaitTime|ThisTime):\s*(-?\d+)',report)}
    values['valid_activity_timing']=values.get('TotalTime',-1)>=0
    values['dock_observed_upper_ms']=dock_observed(start)
    try:
        marker=json.loads(run('exec-out','run-as','de.glasslauncher.tv','cat','cache/startup-last.json'))
        if marker['pid']==int(run('shell','pidof','de.glasslauncher.tv').strip()):
            values['dock_ready_device_ms']=marker['dockReady']-device_start
            values['create_to_dock_ms']=marker['dockReady']-marker['created']
    except (subprocess.CalledProcessError,ValueError,KeyError): pass
    results['cold'].append(values)
    external(); start=time.monotonic(); report=run('shell','am','start','-W','-n','de.glasslauncher.tv/.MainActivity')
    (out/f'return-{i}.txt').write_text(report)
    values={k:int(v) for k,v in re.findall(r'(TotalTime|WaitTime|ThisTime):\s*(-?\d+)',report)}
    values['valid_activity_timing']=values.get('TotalTime',-1)>=0
    values['dock_observed_upper_ms']=dock_observed(start); results['return'].append(values)
    external(); start=time.monotonic(); run('shell','input','keyevent','KEYCODE_HOME'); transitions=[]
    while True:
        top=foreground(); transitions.append({'ms':round((time.monotonic()-start)*1000),'activity':top})
        if 'de.glasslauncher.tv/' in top: break
        if time.monotonic()-start>15: raise RuntimeError('Existing HOME helper did not open Glass; no helper setting changed')
        time.sleep(.1)
    results['home_helper'].append({'activity_resumed_upper_ms':transitions[-1]['ms'], 'transitions':transitions,
                                  'dock_observed_upper_ms':dock_observed(start)})
(out/'startup.json').write_text(json.dumps(results, indent=2))
run('shell','input','keyevent','KEYCODE_DPAD_DOWN')
run('shell','uiautomator','dump','/data/local/tmp/glass-benchmark.xml')
run('shell','dumpsys','gfxinfo','de.glasslauncher.tv','reset')
pid=run('shell','pidof','de.glasslauncher.tv').strip(); assert pid.isdigit()
(out/'mem-before-navigation.txt').write_text(run('shell','dumpsys','meminfo','de.glasslauncher.tv'))
# Intentional input cadence, not a readiness sleep. Only navigation keys; no app launches/settings edits.
run('shell','for i in $(seq 1 20); do input keyevent 22; sleep 0.1; done; for i in $(seq 1 20); do input keyevent 21; sleep 0.1; done')
(out/'gfx-navigation.txt').write_text(run('shell','dumpsys','gfxinfo','de.glasslauncher.tv','framestats'))
(out/'mem-after-navigation.txt').write_text(run('shell','dumpsys','meminfo','de.glasslauncher.tv'))
(out/'cpu.txt').write_text(run('shell','dumpsys','cpuinfo'))
(out/'process-logcat.txt').write_text(run('logcat','-d','--pid='+pid,'-v','brief'))
(out/'accessibility-after.txt').write_text(run('shell','settings','get','secure','enabled_accessibility_services'))
assert (out/'accessibility-before.txt').read_bytes()==(out/'accessibility-after.txt').read_bytes()
(out/'startup.json').write_text(json.dumps(results, indent=2))
print(json.dumps(results, indent=2))
