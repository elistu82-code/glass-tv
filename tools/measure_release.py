#!/usr/bin/env python3
"""Bounded release probe through the installed test APK; never terminates user/system apps.

Build/install :app:assembleDebugAndroidTest first, with /usr/bin/adb --no-incremental.
The probe preserves other accessibility services, including Home on Fire.
Raw activity logs stay local: prefer an ignored artifacts/ output directory.
"""
import argparse
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--device', required=True)
parser.add_argument('--phase', choices=['before', 'after'], required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
if not re.fullmatch(r'[A-Za-z0-9_.:-]+', args.device):
    parser.error('Invalid ADB serial')
args.output.mkdir(parents=True, exist_ok=True)
adb = ['/usr/bin/adb', '-s', args.device]

def read(*command):
    return subprocess.check_output(adb + list(command), timeout=45)

# Dismiss an actual dream only; never issue Back against a user's foreground application.
if b'mCurrentDreamToken=null' not in read('shell', 'dumpsys', 'dreams'):
    read('shell', 'input', 'keyevent', 'KEYCODE_BACK')
settings = [('accessibility.txt', 'enabled_accessibility_services'),
            ('listeners.txt', 'enabled_notification_listeners')]
for name, key in settings:
    (args.output / name).write_bytes(read('shell', 'settings', 'get', 'secure', key))
with (args.output / 'activity-log.txt').open('wb') as stream:
    logger = subprocess.Popen(adb + ['logcat', '-v', 'monotonic', '-T', '1', '-s',
                                    'ActivityTaskManager:I', 'ActivityManager:I', 'GlassProbe:I', '*:S'],
                              stdout=stream, stderr=subprocess.STDOUT)
    try:
        result = subprocess.run(adb + ['shell', 'am', 'instrument', '-w', '-e', 'phase', args.phase,
                                      'de.glasslauncher.tv.test/de.glasslauncher.tv.testing.ReleaseProbe'],
                                capture_output=True, text=True, timeout=180)
    finally:
        logger.terminate()
        logger.wait(timeout=5)
(args.output / 'probe.txt').write_text(result.stdout + result.stderr)
if 'INSTRUMENTATION_CODE: 0' not in result.stdout or 'error=' in result.stdout:
    raise SystemExit('Probe failed; inspect probe.txt. No automatic retry or app termination.')
names = ['release.json', 'navigation.txt', 'memory.txt', 'process-cpu.txt', 'pid.txt',
         'cpu-start.txt', 'cpu-end.txt'] + [f'home-{index}-frames.txt' for index in range(3)]
for name in names:
    (args.output / name).write_bytes(read('exec-out', 'run-as', 'de.glasslauncher.tv.test',
                                       'cat', f'files/{args.phase}-{name}'))
for name, key in settings:
    if (args.output / name).read_bytes() != read('shell', 'settings', 'get', 'secure', key):
        raise SystemExit(f'Unexpected settings difference: {key}')
print((args.output / 'release.json').read_text())
