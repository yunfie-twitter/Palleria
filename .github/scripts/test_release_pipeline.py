import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import release_pipeline as pipeline


def release(rid=10, tag="v2.0", names=("a.apk", "b.apk"), draft=False):
    return {"id": rid, "tag_name": tag, "draft": draft, "prerelease": False,
            "assets": [{"id": i, "name": name, "size": 3, "state": "uploaded",
                        "digest": "sha256:" + hashlib.sha256(b"apk").hexdigest()}
                       for i, name in enumerate(names)]}


class PipelineTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.public = self.root / "public"
        self.public.mkdir()
        self.event = self.root / "event.json"
        self.outputs = self.root / "output"
        self.enterContext(patch.object(pipeline, "PUBLIC", self.public))
        self.enterContext(patch.dict(os.environ, GITHUB_REPOSITORY="owner/repo",
                                     GITHUB_EVENT_PATH=str(self.event), GITHUB_OUTPUT=str(self.outputs)))
        self.enterContext(patch.object(pipeline.time, "sleep"))

    def event_data(self, action, value):
        self.event.write_text(json.dumps({"action": action, "release": value}))

    def write_download(self, *args):
        directory = Path(args[args.index("--dir") + 1])
        for name in ("a.apk", "b.apk"):
            (directory / name).write_bytes(b"apk")
        return ""

    def test_version_gradle_assignment_and_legacy(self):
        for text in ('versionName = "2.0"\nversionCode = 12', "versionName '2.0'\nversionCode 12"):
            self.assertEqual(pipeline.version(text), ("2.0", 12))
        with self.assertRaises(ValueError):
            pipeline.version('versionName = "$(bad)"\nversionCode = 12')

    def test_guard_rejects_public_but_accepts_draft_or_missing(self):
        with patch.object(pipeline, "api", return_value=release()):
            with self.assertRaises(RuntimeError):
                pipeline.guard("v2.0")
        for value in (None, release(draft=True)):
            with patch.object(pipeline, "api", return_value=value):
                pipeline.guard("v2.0")

    def test_api_does_not_treat_authentication_failure_as_missing(self):
        with patch.object(pipeline.subprocess, "run") as command:
            command.return_value.returncode = 1
            command.return_value.stderr = "gh: Bad credentials (HTTP 401)"
            with self.assertRaises(RuntimeError):
                pipeline.api("example", missing_ok=True)
            command.return_value.stderr = "gh: Not Found (HTTP 404)"
            self.assertIsNone(pipeline.api("example", missing_ok=True))

    def test_download_all_apks_and_checksum(self):
        with patch.object(pipeline, "api", return_value=release()), patch.object(pipeline, "run", side_effect=self.write_download) as command:
            pipeline.download(10, self.root)
        self.assertEqual({p.name for p in self.root.glob("*.apk")}, {"a.apk", "b.apk"})
        self.assertIn("*.apk", command.call_args.args)

    def test_no_apks_or_draft_or_deleted_skips(self):
        for value in (release(names=()), release(draft=True), None):
            with patch.object(pipeline, "api", return_value=value), patch.object(pipeline, "run") as command:
                self.assertIsNone(pipeline.download(10, self.root))
                command.assert_not_called()

    def test_asset_changed_during_download_retries_whole_snapshot(self):
        changed = release()
        changed["assets"][0]["id"] = 99
        with patch.object(pipeline, "api", side_effect=[release(), changed, changed, changed]), patch.object(pipeline, "run", side_effect=self.write_download) as command:
            pipeline.download(10, self.root)
        self.assertEqual(command.call_count, 2)

    def test_checksum_failure_never_installs_partial_files(self):
        broken = release()
        broken["assets"][0]["digest"] = "sha256:" + "0" * 64
        with patch.object(pipeline, "api", return_value=broken), patch.object(pipeline, "run", side_effect=self.write_download) as command:
            with self.assertRaisesRegex(RuntimeError, "checksum"):
                pipeline.download(10, self.root)
        self.assertEqual(command.call_count, 3)
        self.assertEqual(list(self.root.glob("*.apk")), [])

    def test_missing_file_retries_and_fails_without_partial_install(self):
        def incomplete(*args):
            (Path(args[-1]) / "a.apk").write_bytes(b"apk")
        with patch.object(pipeline, "api", return_value=release()), patch.object(pipeline, "run", side_effect=incomplete):
            with self.assertRaisesRegex(RuntimeError, "Incomplete"):
                pipeline.download(10, self.root)
        self.assertFalse((self.root / "a.apk").exists())

    def test_unsafe_asset_names_rejected(self):
        for name in ("../a.apk", "dir/a.apk", "dir\\a.apk", ".a.apk"):
            with self.assertRaises(ValueError):
                pipeline.apk_assets(release(names=(name,)))

    def test_publish_uses_tag_metadata_and_records_all_assets(self):
        self.event_data("published", release())
        with patch.object(pipeline, "api", return_value=release()), patch.object(pipeline, "run", side_effect=self.write_download), patch.object(pipeline, "tag_metadata", return_value=("2.0", 20)) as metadata:
            pipeline.sync()
        metadata.assert_called_once_with("v2.0")
        state = json.loads((self.public / "metadata/release-assets.json").read_text())
        self.assertEqual(state["10"]["files"], ["a.apk", "b.apk"])
        self.assertEqual(state["10"]["version_code"], 20)
        self.assertEqual(self.outputs.read_text(), "changed=true\n")

    def test_publish_without_apk_does_not_deploy(self):
        self.event_data("published", release(names=()))
        with patch.object(pipeline, "api", return_value=release(names=())):
            pipeline.sync()
        self.assertEqual(self.outputs.read_text(), "changed=false\n")

    def test_delete_legacy_release_preserves_other_apks_and_docs(self):
        self.event_data("deleted", release())
        (self.public / "repo").mkdir()
        for name in ("a.apk", "b.apk", "other.apk"):
            (self.public / "repo" / name).write_bytes(b"apk")
        (self.public / "index.html").write_text("docs")
        with patch.object(pipeline, "api", return_value=None):
            pipeline.sync()
        self.assertEqual([p.name for p in (self.public / "repo").glob("*.apk")], ["other.apk"])
        self.assertEqual((self.public / "index.html").read_text(), "docs")

    def test_delete_uses_ledger_when_event_assets_empty(self):
        self.event_data("deleted", release(names=()))
        (self.public / "repo").mkdir()
        (self.public / "repo/a.apk").write_bytes(b"apk")
        (self.public / "metadata").mkdir()
        (self.public / "metadata/release-assets.json").write_text(json.dumps({"10": {"files": ["a.apk"]}}))
        with patch.object(pipeline, "api", return_value=None):
            pipeline.sync()
        self.assertFalse((self.public / "repo/a.apk").exists())

    def test_stale_delete_cannot_remove_recreated_release_assets(self):
        self.event_data("deleted", release())
        (self.public / "repo").mkdir()
        (self.public / "repo/a.apk").write_bytes(b"apk")
        (self.public / "metadata").mkdir()
        (self.public / "metadata/release-assets.json").write_text(json.dumps({"11": {"files": ["a.apk"]}}))
        with patch.object(pipeline, "api", return_value=None):
            pipeline.sync()
        self.assertTrue((self.public / "repo/a.apk").exists())

    def test_recommendation_is_highest_stable_code_not_publish_order(self):
        versions = [("2.0", 20, False), ("1.0", 10, False), ("3.0-beta.1", 30, False), ("3.0", 31, True)]
        self.assertEqual(pipeline.recommended(versions), ("2.0", 20))
        self.assertEqual(pipeline.recommended(list(reversed(versions))), ("2.0", 20))
        self.assertEqual(pipeline.recommended(versions[1:]), ("1.0", 10))
        self.assertIsNone(pipeline.recommended(versions[2:]))

    def test_deleted_last_stable_clears_current_version(self):
        text = "License: GPL-3.0-only\nCurrentVersion: '2.0'\nCurrentVersionCode: 20\n"
        self.assertEqual(pipeline.set_current(text, None), "License: GPL-3.0-only\nCurrentVersionCode: 0\n")
        self.assertIn('CurrentVersion: "1.0"', pipeline.set_current(text, ("1.0", 10)))

    def test_apk_version_validates_application_id(self):
        with patch.object(pipeline, "run", return_value="package: name='com.yunfie.illustia' versionCode='20' versionName='2.0'"):
            self.assertEqual(pipeline.apk_version("aapt", "a.apk"), ("2.0", 20))
        with patch.object(pipeline, "run", return_value="package: name='other.app' versionCode='20' versionName='2.0'"):
            with self.assertRaises(ValueError):
                pipeline.apk_version("aapt", "a.apk")


    def test_prepare_preserves_tag_if_commit_differs(self):
        def git(*args):
            if args[1] == "ls-remote":
                return "oldsha refs/tags/v2.0"
            if args[1] == "rev-parse":
                return "oldsha" if args[2] == "FETCH_HEAD^{commit}" else "newsha"
            return ""
        with patch.dict(os.environ, GITHUB_REF="refs/heads/main"), patch.object(pipeline.Path, "read_text", return_value='versionName = "2.0"\nversionCode = 20'), patch.object(pipeline, "guard"), patch.object(pipeline, "run", side_effect=git) as command:
            with self.assertRaisesRegex(RuntimeError, "tag was preserved"):
                pipeline.prepare()
        self.assertFalse(any(call.args[1] in ("tag", "push") for call in command.call_args_list))

    def test_prepare_can_retry_an_existing_tag_at_the_same_commit(self):
        def git(*args):
            if args[1] == "ls-remote":
                return "sha refs/tags/v2.0"
            if args[1] == "rev-parse":
                return "sha"
            return ""
        with patch.dict(os.environ, GITHUB_REF="refs/tags/v2.0"), patch.object(pipeline.Path, "read_text", return_value='versionName = "2.0"\nversionCode = 20'), patch.object(pipeline, "guard"), patch.object(pipeline, "run", side_effect=git):
            pipeline.prepare()
        self.assertEqual(self.outputs.read_text(), "tag=v2.0\nprerelease=false\n")

    def test_prepare_rejects_version_tag_mismatch(self):
        with patch.dict(os.environ, GITHUB_REF="refs/tags/v1.0"), patch.object(pipeline.Path, "read_text", return_value='versionName = "2.0"\nversionCode = 20'), patch.object(pipeline, "guard") as guard:
            with self.assertRaises(ValueError):
                pipeline.prepare()
            guard.assert_not_called()

    def test_index_checks_tag_versions_and_escapes_secret_config(self):
        tools = self.root / "build-tools/37.0.0"
        tools.mkdir(parents=True)
        (tools / "aapt").touch()
        (self.public / "repo").mkdir()
        (self.public / "repo/a.apk").write_bytes(b"apk")
        (self.public / "metadata").mkdir()
        (self.public / "metadata/release-assets.json").write_text(json.dumps({"10": {
            "files": ["a.apk"], "version_name": "2.0", "version_code": 20, "prerelease": False}}))
        metadata = self.public / f"metadata/{pipeline.APP_ID}.yml"
        metadata.write_text("License: GPL-3.0-only\nCurrentVersion: old\nCurrentVersionCode: 1\n")
        old_cwd = Path.cwd()
        os.chdir(self.root)
        self.addCleanup(os.chdir, old_cwd)
        secrets = {"ANDROID_HOME": str(self.root), "KEYSTORE_BASE64": "a2V5\n", "KEYSTORE_PASSWORD": 'quote"\\\nsecret',
                   "KEY_PASSWORD": "keypass", "KEY_ALIAS": "alias"}
        with patch.dict(os.environ, secrets), patch.object(pipeline, "apk_version", return_value=("9.0", 90)):
            with self.assertRaisesRegex(ValueError, "does not match"):
                pipeline.index()
        self.assertFalse((self.public / "config.yml").exists())
        with patch.dict(os.environ, secrets), patch.object(pipeline, "apk_version", return_value=("2.0", 20)):
            pipeline.index()
        self.assertIn('CurrentVersion: "2.0"', metadata.read_text())
        config = json.loads((self.public / "config.yml").read_text())
        self.assertEqual(config["keystorepass"], secrets["KEYSTORE_PASSWORD"])
        self.assertEqual((self.root / "release.keystore").read_bytes(), b"key")


if __name__ == "__main__":
    unittest.main()
