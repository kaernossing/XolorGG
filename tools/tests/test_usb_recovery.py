# SPDX-License-Identifier: GPL-3.0-only
import importlib.util
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('usb_recovery', Path(__file__).parents[1] / 'usb_recovery.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def status(**changes):
    value = dict(runEnabled=True, appVersionCode=16030002, agentConnected=False,
                 agentVersionCode=-1, pid=-1, lifecycle='ABSENT', wakeActive=False,
                 desktopRequested=True, desktopEnabled=False)
    value.update(changes)
    return value


class RecoveryTests(unittest.TestCase):
    def test_only_confirmed_usb_device_can_activate(self):
        listing = ('List of devices attached\nother device usb:1-1 transport_id:4\n'
                   'target unauthorized usb:1-2 transport_id:5\n')
        self.assertEqual((None, 'unauthorized'), m.usb_transport(listing, 'target'))
        self.assertEqual((None, 'disconnected'), m.usb_transport(listing, 'absent'))
        self.assertEqual((None, 'not_usb'), m.usb_transport('target device transport_id:7', 'target'))
        self.assertEqual(('8', 'device'), m.usb_transport('target device usb:1-2 transport_id:8', 'target'))

    def test_stable_five_seconds_and_replug_resets_budget(self):
        w = m.ConnectionWindow()
        self.assertFalse(w.observe('1', 0))
        self.assertFalse(w.observe('1', 4.99))
        self.assertTrue(w.observe('1', 5))
        for now in [5, 10, 15]:
            self.assertTrue(w.observe('1', now))
            w.attempted(now)
        self.assertFalse(w.observe('1', 999))
        self.assertFalse(w.observe(None, 1000))
        self.assertFalse(w.observe('2', 1001))
        self.assertTrue(w.observe('2', 1006))
        self.assertEqual(0, w.attempts)

    def test_reenumeration_without_observed_disconnect_restarts_stability(self):
        w = m.ConnectionWindow()
        w.observe('1', 0)
        self.assertFalse(w.observe('2', 4))
        self.assertFalse(w.observe('2', 8))
        self.assertTrue(w.observe('2', 9))

    def test_stop_and_unknown_versions_never_activate(self):
        self.assertEqual('master_off', m.decision(status(runEnabled=False), False, 16030002))
        self.assertEqual('version_mismatch', m.decision(status(appVersionCode=16030001), False, 16030002))
        self.assertEqual('agent_version_mismatch', m.decision(status(agentConnected=True, agentVersionCode=16030001), True, 16030002))

    def test_healthy_or_unready_existing_process_never_replaced(self):
        self.assertEqual('existing_not_ready', m.decision(status(), True, 16030002))
        ready = status(agentConnected=True, agentVersionCode=16030002, pid=42,
                       lifecycle='RUNNING', wakeActive=True, desktopEnabled=True)
        self.assertEqual('ready', m.decision(ready, True, 16030002))
        ready['wakeActive'] = False
        self.assertEqual('existing_not_ready', m.decision(ready, True, 16030002))
        self.assertEqual('activate', m.decision(status(), False, 16030002))

    def test_camera_agent_prevents_recovery_even_if_google_was_enabled(self):
        self.assertEqual('colorcamera_conflict', m.decision(status(), False, 16030002, camera_running=True))
        self.assertEqual('activate', m.decision(status(), False, 16030002, camera_running=False))

    def test_disabled_desktop_does_not_block_other_features(self):
        value = status(agentConnected=True, agentVersionCode=16030002, lifecycle='RUNNING',
                       wakeActive=True, desktopRequested=False)
        self.assertEqual('ready', m.decision(value, True, 16030002))

    def test_terminal_stop_stays_off_for_same_connection(self):
        w = m.ConnectionWindow()
        w.observe('1', 0); w.attempted(5, terminal=True)
        self.assertFalse(w.observe('1', 999))

    def test_compact_response_is_strict_and_rejects_diagnostic_text(self):
        value = status()
        raw = 'Result: Bundle[{' + ', '.join(k + '=' + str(v).lower() if isinstance(v, bool)
                  else k + '=' + str(v) for k, v in value.items()) + '}]'
        self.assertEqual(value, m.parse_status(raw))
        for bad in ['Result: Bundle[{runEnabled=true}]', raw.replace('true', 'yes'),
                    raw.replace('}]', ', runEnabled=true}]'), raw + '\nrunEnabled=true']:
            with self.assertRaises(ValueError): m.parse_status(bad)

    @patch.object(m.subprocess, 'run')
    def test_all_phone_commands_pin_serial_and_port(self, run):
        adb = m.Adb(dict(adb='/test/adb', port=5038, serial='target'))
        run.return_value = subprocess.CompletedProcess([], 0, '', '')
        adb.run(['shell', 'echo', 'test'])
        self.assertEqual(['/test/adb', '-P', '5038', '-s', 'target', 'shell', 'echo', 'test'], run.call_args.args[0])
        adb.run(['devices', '-l'], device=False)
        self.assertEqual(['/test/adb', '-P', '5038', 'devices', '-l'], run.call_args.args[0])

    @patch.object(m.subprocess, 'run')
    def test_failed_pid_inspection_is_not_mistaken_for_absence(self, run):
        adb = m.Adb(dict(adb='/test/adb', port=5038, serial='target'))
        run.return_value = subprocess.CompletedProcess([], 1, '', 'device offline')
        with self.assertRaises(ValueError): adb.pid_exists()
        run.return_value = subprocess.CompletedProcess([], 1, '', '')
        self.assertFalse(adb.pid_exists())


if __name__ == '__main__':
    unittest.main()
