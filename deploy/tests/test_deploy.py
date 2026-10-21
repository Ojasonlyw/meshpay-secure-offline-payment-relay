"""Exercise deployment failure paths without needing SSH or a remote registry."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

REPOSITORY = Path(__file__).resolve().parents[2]
OLD = "ghcr.io/example/meshpay@sha256:" + "a" * 64
NEW = "ghcr.io/example/meshpay@sha256:" + "b" * 64

MOCK_DOCKER = '''#!/usr/bin/env python3
import json, os, pathlib, sys
args = sys.argv[1:]
with open(os.environ["COMMAND_LOG"], "a") as log:
    log.write(json.dumps(args) + "\\n")
state = pathlib.Path(os.environ["IMAGE_STATE"])
if args[0] == "inspect":
    print(state.read_text())
    sys.exit(0)
env_files = [args[i + 1] for i, arg in enumerate(args) if arg == "--env-file"]
image = pathlib.Path(env_files[-1]).read_text().strip().split("=", 1)[1]
if "exec" in args:
    if os.environ.get("FAIL_BACKUP") == "1":
        sys.exit(21)
    print("database snapshot")
elif "up" in args and args[-1] == "app":
    if image == os.environ.get("FAIL_IMAGE"):
        state.write_text(image)
        sys.exit(17)
    state.write_text(image)
elif "ps" in args:
    print("app-container")
'''


class DeploymentTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / ".env").write_text("POSTGRES_PASSWORD=test-only\n")
        self.old = self.release("old", OLD)
        self.new = self.release("new")
        (self.root / "current").symlink_to(self.old)
        binary = self.root / "bin"
        binary.mkdir()
        docker = binary / "docker"
        docker.write_text(MOCK_DOCKER)
        docker.chmod(0o700)
        self.log = self.root / "commands.jsonl"
        self.state = self.root / "image.state"
        self.state.write_text(OLD)
        self.env = dict(os.environ, PATH=str(binary) + os.pathsep + os.environ["PATH"],
                        COMMAND_LOG=str(self.log), IMAGE_STATE=str(self.state))

    def release(self, name, image=None):
        directory = self.root / "releases" / name
        directory.mkdir(parents=True)
        shutil.copy(REPOSITORY / "deploy/deploy.sh", directory / "deploy.sh")
        shutil.copy(REPOSITORY / "compose.production.yaml", directory)
        if image:
            (directory / "image.env").write_text("MESHPAY_IMAGE=" + image + "\n")
        return directory

    def run_release(self, image=NEW, **settings):
        return subprocess.run(["bash", str(self.new / "deploy.sh"), str(self.root), image],
                              env=dict(self.env, **settings), capture_output=True, text=True)

    def commands(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()]

    def test_success_promotes_exact_image_after_backup(self):
        result = self.run_release()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((self.root / "current").resolve(), self.new)
        self.assertEqual((self.root / "previous").resolve(), self.old)
        self.assertEqual(self.state.read_text(), NEW)
        self.assertEqual(len(list((self.root / "backups").glob("*.dump"))), 1)
        commands = self.commands()
        backup_index = next(i for i, args in enumerate(commands) if "exec" in args)
        app_index = next(i for i, args in enumerate(commands) if "up" in args and args[-1] == "app")
        self.assertLess(backup_index, app_index)

    def test_unhealthy_release_restores_previous_and_still_fails(self):
        result = self.run_release(FAIL_IMAGE=NEW)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Previous application image restored", result.stderr)
        self.assertEqual((self.root / "current").resolve(), self.old)
        self.assertEqual(self.state.read_text(), OLD)

    def test_first_deployment_has_no_fictitious_previous_release(self):
        (self.root / "current").unlink()
        result = self.run_release()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((self.root / "current").resolve(), self.new)
        self.assertFalse((self.root / "previous").is_symlink())

    def test_failed_backup_blocks_application_replacement(self):
        result = self.run_release(FAIL_BACKUP="1")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.state.read_text(), OLD)
        self.assertFalse(any("up" in args and args[-1] == "app" for args in self.commands()))

    def test_mutable_image_tag_is_rejected_before_docker(self):
        result = self.run_release(image="ghcr.io/example/meshpay:latest")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.log.exists())

    def test_existing_release_cannot_be_overwritten_with_another_image(self):
        (self.new / "image.env").write_text("MESHPAY_IMAGE=" + OLD + "\n")
        result = self.run_release()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("cannot be reused", result.stderr)
        self.assertFalse(self.log.exists())


if __name__ == "__main__":
    unittest.main()
