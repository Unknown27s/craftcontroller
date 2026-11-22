#!/usr/bin/env python3
"""Package the actual Gradle APK metadata for a debug-signed GitHub prerelease."""

import hashlib
import json
import os
from pathlib import Path
import re
import shutil


def main() -> None:
    metadata_file = Path("app/build/outputs/apk/debug/output-metadata.json")
    metadata = json.loads(metadata_file.read_text())
    if metadata["applicationId"] != "io.github.unknown27s.dsucontroller":
        raise ValueError("Unexpected application ID")
    if len(metadata["elements"]) != 1:
        raise ValueError("Expected exactly one APK")
    element = metadata["elements"][0]
    version = element["versionName"]
    code = element["versionCode"]
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?", version):
        raise ValueError("Unsupported version name")
    if not isinstance(code, int) or code < 1:
        raise ValueError("Invalid version code")
    source_name = element["outputFile"]
    if Path(source_name).name != source_name:
        raise ValueError("APK must be alongside its metadata")
    source = metadata_file.parent / source_name
    output = Path("dist")
    output.mkdir(exist_ok=True)
    filename = f"craftcontroller-{version}.apk"
    destination = output / filename
    shutil.copyfile(source, destination)
    digest = hashlib.sha256(destination.read_bytes()).hexdigest()
    (output / "SHA256SUMS").write_text(f"{digest}  {filename}\n")
    details = {
        "versionName": version,
        "versionCode": code,
        "applicationId": metadata["applicationId"],
        "commit": os.environ.get("GITHUB_SHA", "local"),
        "sha256": digest,
        "signing": "debug",
    }
    (output / "version.json").write_text(json.dumps(details, indent=2) + "\n")
    notes = f"""## CraftController {version}

- Android 8.0+ (API 26); version code **{code}**.
- Download **{filename}** below. `SHA256SUMS` verifies this APK; `version.json` records its version and source commit.
- This is a **debug-signed prototype prerelease**, not a production release.
- GitHub builds use a temporary debug signing key. It can differ from a local build or another workflow run. If Android reports a signature conflict, uninstall the previous app before installing; uninstalling deletes saved layouts and endpoints. Do not disable Android security checks.
- Tests and lint run only when explicitly selected in the workflow. Packaging alone is not physical-device validation.
- DSU Manager QR scanning saves an endpoint only; the post-scan pairing handshake is not implemented. Windows controller output remains unverified.
- No motion, rumble or background streaming. Keep the UDP listener on a trusted LAN.

Source commit: `{details['commit']}`.
"""
    (output / "RELEASE_NOTES.md").write_text(notes)
    summary = f"Version: **{version}** · build **{code}** · debug-signed prototype\n"
    print(summary)
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as stream:
            stream.write(f"version={version}\ntag=v{version}\n")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as stream:
            stream.write(summary)


if __name__ == "__main__":
    main()
