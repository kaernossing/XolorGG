#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Install/remove this user's USB helper; no administrator access needed."""
import argparse
import json
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import sys

LABEL = 'dev.colorgoogle.usb-recovery'
ROOT = Path.home() / 'Library/Application Support/ColorGoogle USB Recovery'
PLIST = Path.home() / 'Library/LaunchAgents' / (LABEL + '.plist')


def main():
    p = argparse.ArgumentParser()
    p.add_argument('action', choices=['install', 'uninstall', 'status'])
    p.add_argument('--serial')
    p.add_argument('--port', type=int, default=5038)
    p.add_argument('--version', type=int, default=17000100)
    args = p.parse_args()
    domain = 'gui/' + str(os.getuid())
    if args.action == 'status':
        raise SystemExit(subprocess.run(['launchctl', 'print', domain + '/' + LABEL]).returncode)
    if args.action == 'install' and not args.serial:
        p.error('--serial must identify the user-confirmed phone')
    loaded = subprocess.run(['launchctl', 'print', domain + '/' + LABEL], capture_output=True).returncode == 0
    if loaded:
        subprocess.run(['launchctl', 'bootout', domain + '/' + LABEL], check=True)
    if args.action == 'uninstall':
        PLIST.unlink(missing_ok=True)
        for name in ['usb_recovery.py', 'activate-agent.sh', 'config.json', 'helper.lock']:
            (ROOT / name).unlink(missing_ok=True)
        print('Removed startup/helper files; diagnostic logs retained at', ROOT)
        return
    adb = shutil.which('adb')
    if not adb:
        raise SystemExit('ADB not found; install official Platform-Tools first')
    ROOT.mkdir(parents=True, exist_ok=True, mode=0o700)
    source = Path(__file__).resolve().parent
    for name in ['usb_recovery.py', 'activate-agent.sh']:
        shutil.copy2(source / name, ROOT / name)
    config = dict(adb=adb, port=args.port, serial=args.serial, version=args.version,
                  starter=str(ROOT / 'activate-agent.sh'))
    (ROOT / 'config.json').write_text(json.dumps(config, indent=2) + '\n')
    (ROOT / 'config.json').chmod(0o600)
    PLIST.parent.mkdir(parents=True, exist_ok=True)
    PLIST.write_bytes(plistlib.dumps(dict(Label=LABEL,
        ProgramArguments=[sys.executable, str(ROOT / 'usb_recovery.py'), '--config', str(ROOT / 'config.json')],
        RunAtLoad=True, KeepAlive=False, ProcessType='Background')))
    subprocess.run(['launchctl', 'enable', domain + '/' + LABEL], check=True)
    subprocess.run(['launchctl', 'bootstrap', domain, str(PLIST)], check=True)
    print('Installed', PLIST)


if __name__ == '__main__':
    main()
