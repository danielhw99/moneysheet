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


    def test_alternative_image_declarations_are_rejected(self):
        for declaration in ('FE_IMAGE: other', ' FE_IMAGE =other', 'export FE_IMAGE=other', 'FE_IMAGE'):
            for duplicate in (True,False):
                with self.subTest(declaration=declaration,duplicate=duplicate):
                    content=(self.original+declaration+'\n') if duplicate else self.original.replace('FE_IMAGE=old-fe',declaration)
                    (self.prod/'.env').write_text(content)
                    self.assertNotEqual(self.run_pull().returncode,0)
                    self.assertEqual((self.prod/'.env').read_text(),content)
                    self.assertFalse((self.prod/'releases').exists())

    def python_update(self):
        return SCRIPT.read_text().split("<<'PY'\n",1)[1].rsplit('\nPY',1)[0]

    def test_snapshot_change_aborts_replacement(self):
        # Simulate an uncooperative writer after the snapshot was read.
        wrapper = """import sys,tempfile
from unittest.mock import patch
from pathlib import Path
code=sys.stdin.read()
create_temp=tempfile.mkstemp
def changed(*args,**kwargs):
    path=Path(sys.argv[1])/'.env'
    path.write_text(path.read_text().replace('example.invalid','changed.invalid'))
    return create_temp(*args,**kwargs)
with patch('tempfile.mkstemp',side_effect=changed):exec(compile(code,'pull-update','exec'))
"""
        result=subprocess.run(['python3','-c',wrapper,str(self.prod),*self.refs,*(['a'*40]*3)],input=self.python_update(),text=True,capture_output=True)
        self.assertNotEqual(result.returncode,0)
        self.assertIn('.env changed during update',result.stderr)
        self.assertIn('changed.invalid',(self.prod/'.env').read_text())
        self.assertIn('FE_IMAGE=old-fe',(self.prod/'.env').read_text())
        self.assertFalse(list(self.prod.glob('.env-images-*')))

    def test_shared_lock_serializes_writer_and_preserves_other_settings(self):
        import fcntl
        import time
        marker=self.root/'attempted-lock'
        wrapper="""import fcntl,sys
from pathlib import Path
original=fcntl.flock
def observed(*args,**kwargs):
    (Path(sys.argv[1]).parent/'attempted-lock').touch()
    return original(*args,**kwargs)
fcntl.flock=observed
exec(compile(sys.stdin.read(),'pull-update','exec'))
"""
        with (self.prod/'.env.lock').open('a') as lock:
            fcntl.flock(lock,fcntl.LOCK_EX)
            process=subprocess.Popen(['python3','-c',wrapper,str(self.prod),*self.refs,*(['a'*40]*3)],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
            try:
                process.stdin.write(self.python_update())
                process.stdin.close()
                process.stdin=None
                deadline=time.monotonic()+5
                while not marker.exists() and time.monotonic()<deadline:time.sleep(0.01)
                self.assertTrue(marker.exists(),'child must reach lock acquisition')
                self.assertIsNone(process.poll(),'update must wait for the shared lock')
                (self.prod/'.env').write_text(self.original.replace('example.invalid','concurrent.invalid'))
                fcntl.flock(lock,fcntl.LOCK_UN)
                out,err=process.communicate(timeout=5)
                self.assertEqual(process.returncode,0,err)
            finally:
                if process.poll() is None:process.kill()
                process.communicate()
        updated=(self.prod/'.env').read_text()
        self.assertIn('concurrent.invalid',updated)
        for ref in self.refs:self.assertIn(ref,updated)


    def test_digest_write_failure_preserves_environment_and_cleans_partial_record(self):
        wrapper="""import errno,os,sys
from contextlib import contextmanager
from unittest.mock import patch,Mock
real_fdopen=os.fdopen
@contextmanager
def fail_record_write(fd,*args,**kwargs):
    record=os.readlink('/proc/self/fd/'+str(fd)).endswith('-images.env')
    with real_fdopen(fd,*args,**kwargs) as stream:
        if record:
            def disk_full(content):
                stream.write(content[:12])
                stream.flush()
                raise OSError(errno.ENOSPC,'Injected digest write disk full')
            proxy=Mock(wraps=stream)
            proxy.write.side_effect=disk_full
            yield proxy
        else:yield stream
with patch('os.fdopen',side_effect=fail_record_write):
    exec(compile(sys.stdin.read(),'pull-update','exec'),{'__name__':'__main__'})
"""
        result=subprocess.run(['python3','-c',wrapper,str(self.prod),*self.refs,*(['a'*40]*3)],input=self.python_update(),text=True,capture_output=True)
        self.assertNotEqual(result.returncode,0)
        self.assertIn('Injected digest write disk full',result.stderr)
        self.assertEqual((self.prod/'.env').read_text(),self.original)
        self.assertFalse(list((self.prod/'releases').glob('*-images.env')))
        self.assertFalse(list(self.prod.glob('.env-images-*')))


    def test_replace_failure_cleans_prepared_digest_record(self):
        wrapper="""import sys
from unittest.mock import patch
with patch('os.replace',side_effect=OSError('Injected replacement failure')):
    exec(compile(sys.stdin.read(),'pull-update','exec'),{'__name__':'__main__'})
"""
        result=subprocess.run(['python3','-c',wrapper,str(self.prod),*self.refs,*(['a'*40]*3)],input=self.python_update(),text=True,capture_output=True)
        self.assertNotEqual(result.returncode,0)
        self.assertIn('Injected replacement failure',result.stderr)
        self.assertEqual((self.prod/'.env').read_text(),self.original)
        self.assertFalse(list((self.prod/'releases').glob('*-images.env')))
        self.assertFalse(list(self.prod.glob('.env-images-*')))

if __name__ == '__main__':unittest.main()
