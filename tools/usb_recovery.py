#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""USB-only activation after a stable connection. Never kill a phone process."""
import argparse
import fcntl
import json
import logging
from logging.handlers import RotatingFileHandler
from pathlib import Path
import re
import subprocess
import time

URI = 'content://dev.evoker.homeholdcts.desktopsearch'
FIELDS = {'runEnabled', 'appVersionCode', 'agentConnected', 'agentVersionCode',
          'pid', 'lifecycle', 'wakeActive', 'desktopRequested', 'desktopEnabled'}
BOOLS = {'runEnabled', 'agentConnected', 'wakeActive', 'desktopRequested', 'desktopEnabled'}
INTS = {'appVersionCode', 'agentVersionCode', 'pid'}


def usb_transport(output, serial):
    for line in output.splitlines():
        parts = line.split()
        if len(parts) < 2 or parts[0] != serial:
            continue
        if parts[1] != 'device':
            return None, parts[1]
        attributes = dict(p.split(':', 1) for p in parts[2:] if ':' in p)
        if 'usb' not in attributes or 'transport_id' not in attributes:
            return None, 'not_usb'
        return attributes['transport_id'], 'device'
    return None, 'disconnected'


def parse_status(output):
    match = re.fullmatch(r'Result: Bundle\[\{([^\n]*)\}\]\s*', output.strip())
    if not match:
        raise ValueError('Invalid compact status response')
    result = {}
    for entry in match.group(1).split(', '):
        key, value = entry.split('=', 1)
        if key not in FIELDS or key in result:
            raise ValueError('Unexpected/duplicate status field')
        if key in BOOLS:
            if value not in ('true', 'false'):
                raise ValueError('Invalid boolean')
            result[key] = value == 'true'
        elif key in INTS:
            result[key] = int(value)
        else:
            if value not in ('ABSENT', 'CONNECTING', 'RUNNING', 'WAITING', 'STOPPING'):
                raise ValueError('Unknown lifecycle')
            result[key] = value
    if result.keys() != FIELDS:
        raise ValueError('Incomplete compact status')
    return result


def decision(status, pid_exists, version, camera_running=False):
    if camera_running:
        return 'colorcamera_conflict'
    if status['appVersionCode'] != version:
        return 'version_mismatch'
    if not status['runEnabled']:
        return 'master_off'
    if status['agentConnected'] and status['agentVersionCode'] != version:
        return 'agent_version_mismatch'
    if status['agentConnected']:
        ready = (status['lifecycle'] == 'RUNNING' and status['wakeActive']
                 and (not status['desktopRequested'] or status['desktopEnabled']))
        return 'ready' if ready else 'existing_not_ready'
    return 'existing_not_ready' if pid_exists else 'activate'


class ConnectionWindow:
    """Three inspections/activation attempts per USB transport; no endless respawn."""
    def __init__(self):
        self.transport = None
        self.since = 0
        self.next_at = 0
        self.attempts = 0
        self.done = False

    def observe(self, transport, now):
        if transport != self.transport:
            self.transport = transport
            self.since = now
            self.next_at = now + 5
            self.attempts = 0
            self.done = False
        return (transport is not None and not self.done and self.attempts < 3
                and now >= self.next_at)

    def attempted(self, now, terminal=False):
        self.attempts += 1
        self.next_at = now + 5
        self.done = terminal or self.attempts >= 3


class Adb:
    def __init__(self, config):
        self.config = config

    def run(self, args, device=True, input_text=None, timeout=10):
        command = [self.config['adb'], '-P', str(self.config['port'])]
        if device:
            command += ['-s', self.config['serial']]
        return subprocess.run(command + args, input=input_text, text=True,
                              capture_output=True, timeout=timeout)

    def status(self):
        result = self.run(['shell', 'content', 'call', '--uri', URI,
                           '--method', 'status', '--arg', 'compact'])
        if result.returncode:
            raise ValueError('Status command failed')
        return parse_status(result.stdout)

    def pid_exists(self, process='colorgoogle-search'):
        result = self.run(['shell', 'pidof', process])
        if result.returncode == 1 and not result.stdout.strip() and not result.stderr.strip():
            return False
        if result.returncode or not re.fullmatch(r'\d+(?:\s+\d+)*\s*', result.stdout):
            raise ValueError('Cannot determine agent PID')
        return True

    def activate(self):
        script = Path(self.config['starter']).read_text()
        return self.run(['shell', 'sh'], input_text=script, timeout=25).returncode


def run(config, logger):
    adb = Adb(config)
    window = ConnectionWindow()
    last_state = None
    while True:
        try:
            devices = adb.run(['devices', '-l'], device=False)
            if devices.returncode:
                raise ValueError('Device list unavailable')
            transport, state = usb_transport(devices.stdout, config['serial'])
            if (transport, state) != last_state:
                logger.info('USB state=%s transport=%s', state, transport)
                last_state = (transport, state)
            if window.observe(transport, time.monotonic()):
                terminal = False
                try:
                    status = adb.status()
                    action = decision(status, adb.pid_exists(), config['version'], adb.pid_exists('colorcamera-agent'))
                    logger.info('CHECK attempt=%s action=%s pid=%s', window.attempts + 1,
                                action, status['pid'])
                    terminal = action in ('master_off', 'ready', 'version_mismatch', 'agent_version_mismatch', 'colorcamera_conflict')
                    if action == 'activate':
                        code = adb.activate()
                        logger.info('ACTIVATE exit=%s', code)
                        # Only connection readiness is checked here; physical acceptance is separate.
                        verified = decision(adb.status(), adb.pid_exists(), config['version'])
                        logger.info('AFTER_ACTIVATE state=%s', verified)
                        terminal = verified in ('ready', 'master_off')
                except (OSError, ValueError, subprocess.TimeoutExpired) as error:
                    logger.error('CHECK_FAILED kind=%s reason=%s', type(error).__name__, str(error)[:160])
                window.attempted(time.monotonic(), terminal)
                if window.done and not terminal:
                    logger.error('RECOVERY_INCOMPLETE attempts=3; inspect phone before retrying')
        except (OSError, ValueError, subprocess.TimeoutExpired) as error:
            # Losing access to the host server is not evidence of a new phone connection.
            logger.error('ADB_UNAVAILABLE kind=%s', type(error).__name__)
        time.sleep(1)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--config', type=Path, required=True)
    args = parser.parse_args()
    config = json.loads(args.config.read_text())
    with (args.config.parent / 'helper.lock').open('w') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise SystemExit('USB helper already running')
        logger = logging.getLogger('colorgoogle-usb')
        logger.setLevel(logging.INFO)
        handler = RotatingFileHandler(args.config.parent / 'helper.log', maxBytes=131072, backupCount=1)
        handler.setFormatter(logging.Formatter('%(asctime)s %(levelname)s %(message)s'))
        logger.addHandler(handler)
        logger.info('START version=%s', config['version'])
        run(config, logger)


if __name__ == '__main__':
    main()
