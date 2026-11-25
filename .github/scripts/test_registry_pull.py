import os
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[2] / 'moneysheet/deploy/production/scripts/pull-images.sh'

class PullImagesTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.prod = self.root / 'production'
        self.prod.mkdir()
        self.original = 'FE_IMAGE=old-fe\nBE_IMAGE=old-be\nMIGRATION_IMAGE=old-migration\nSITE_ADDRESS=example.invalid\n'
        (self.prod / '.env').write_text(self.original)
        config = self.root / '.config/moneysheet-registry'
        config.mkdir(parents=True)
        (config / 'config.json').write_text('{}')
        tools = self.root / 'bin'
        tools.mkdir()
        docker = tools / 'docker'
        docker.write_text('''#!/usr/bin/env python3
import os,sys
args=sys.argv[1:]
with open(os.environ['FAKE_LOG'],'a') as f:f.write(' '.join(args)+'\\n')
if 'pull' in args:
    if os.environ.get('FAIL_PULL') and 'moneysheet-be@' in ' '.join(args):sys.exit(1)
elif 'inspect' in args:
    fmt=args[-1];ref=args[args.index('inspect')+1]
    if 'Architecture' in fmt:print('linux/amd64')
    elif 'revision' in fmt:print(('b' if os.environ.get('MISMATCH') and 'moneysheet-migration@' in ref else 'a')*40)
    elif 'source' in fmt:print('https://github.com/danielhw99/moneysheet'+('_FE' if 'moneysheet-fe@' in ref else ''))
else:sys.exit('Unexpected docker operation')
''')
        docker.chmod(0o700)
        self.env = {**os.environ, 'HOME':str(self.root), 'PATH':str(tools)+os.pathsep+os.environ['PATH'], 'FAKE_LOG':str(self.root/'calls')}
        self.refs = ['ghcr.io/danielhw99/'+name+'@sha256:'+'c'*64 for name in ('moneysheet-fe','moneysheet-be','moneysheet-migration')]

    def run_pull(self, **env):
        return subprocess.run(['bash',str(SCRIPT),*self.refs,str(self.prod)],env={**self.env,**env},capture_output=True,text=True)

    def test_invalid_reference_stops_before_docker(self):
        self.refs[0]='ghcr.io/danielhw99/moneysheet-fe:latest'
        self.assertNotEqual(self.run_pull().returncode,0)
        self.assertFalse((self.root/'calls').exists())

    def test_failed_pull_preserves_environment(self):
        self.assertNotEqual(self.run_pull(FAIL_PULL='1').returncode,0)
        self.assertEqual((self.prod/'.env').read_text(),self.original)
        self.assertFalse((self.prod/'releases').exists())

    def test_mismatched_pair_preserves_environment(self):
        self.assertNotEqual(self.run_pull(MISMATCH='1').returncode,0)
        self.assertEqual((self.prod/'.env').read_text(),self.original)

    def test_success_records_digest_and_backup_without_starting_services(self):
        result=self.run_pull()
        self.assertEqual(result.returncode,0,result.stderr)
        updated=(self.prod/'.env').read_text()
        for ref in self.refs:self.assertIn(ref,updated)
        self.assertIn('SITE_ADDRESS=example.invalid\n',updated)
        self.assertEqual((self.prod/'.env').stat().st_mode & 0o777,0o600)
        backup=list((self.prod/'releases').glob('*.env.backup'))
        self.assertEqual(len(backup),1)
        self.assertEqual(backup[0].read_text(),self.original)
        calls=(self.root/'calls').read_text()
        self.assertEqual(calls.count('pull --platform linux/amd64'),3)
        self.assertNotIn('compose',calls)
        self.assertNotIn(' run ',calls)

if __name__ == '__main__':unittest.main()
