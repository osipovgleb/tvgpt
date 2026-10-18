"""Packaging must keep private audio outside the public source tree."""
import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / 'scripts/prepare-assets.py'


class PrepareAssetsTest(unittest.TestCase):
    def stage(self, source, private, output):
        self.assertTrue(SCRIPT.exists(), 'Private asset staging is not implemented')
        spec = importlib.util.spec_from_file_location('prepare_assets', SCRIPT)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        module.prepare_assets(source, private, output)

    def test_only_expected_audio_is_packaged_without_changing_sources(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, private, output = [root / name for name in ('source', 'private', 'output')]
            source.mkdir()
            private.mkdir()
            (source / 'notice.txt').write_text('notice')
            (private / 'voice-start.ogg').write_bytes(b'original cue')
            (private / 'voice-samples').mkdir()
            (private / 'voice-samples/cove.m4a').write_bytes(b'original preview')
            (private / 'secret.key').write_text('must stay private')
            self.stage(source, private, output)
            self.assertEqual((output / 'voice-start.ogg').read_bytes(), b'original cue')
            self.assertEqual((output / 'voice-samples/cove.m4a').read_bytes(), b'original preview')
            self.assertEqual((output / 'notice.txt').read_text(), 'notice')
            self.assertFalse((output / 'secret.key').exists())
            self.assertFalse((source / 'voice-start.ogg').exists())
            (private / 'voice-start.ogg').unlink()
            self.stage(source, private, output)
            self.assertFalse((output / 'voice-start.ogg').exists(), 'Stale private audio leaked')

    def test_public_build_works_without_private_audio(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / 'source'
            source.mkdir()
            self.stage(source, root / 'absent', root / 'output')
            self.assertTrue((root / 'output').is_dir())


if __name__ == '__main__':
    unittest.main()
