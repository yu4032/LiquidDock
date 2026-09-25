#!/usr/bin/env python3
"""Verify the private Libs release archive and its SurfaceFlinger member in CI."""

import io
import json
import os
from pathlib import Path
import tarfile
import urllib.request

from verify_binary import sha256, verify


API_ROOT = "https://api.github.com/repos/yu4032/hyperos-analysis"


def api_get(url, token, accept="application/vnd.github+json"):
    request = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": accept,
            "User-Agent": "LiquidDock-PassBlur-verifier",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def main():
    token = os.environ.get("HYPEROS_ANALYSIS_READ_TOKEN")
    if not token:
        raise SystemExit("FAIL: HYPEROS_ANALYSIS_READ_TOKEN is required for the private release")
    manifest = json.loads(Path(__file__).with_name("compatibility.json").read_text())
    source = manifest["source"]
    metadata = json.loads(api_get(f"{API_ROOT}/releases/tags/{source['release_tag']}", token))
    candidates = [asset for asset in metadata["assets"] if asset["name"] == source["asset"]]
    if len(candidates) != 1:
        raise SystemExit("FAIL: expected exactly one Libs release asset")
    payload = api_get(
        f"{API_ROOT}/releases/assets/{candidates[0]['id']}",
        token,
        "application/octet-stream",
    )
    actual_asset_sha = sha256(payload)
    if actual_asset_sha != source["asset_sha256"]:
        raise SystemExit(f"FAIL: release archive SHA256 differs: {actual_asset_sha}")
    with tarfile.open(fileobj=io.BytesIO(payload), mode="r:gz") as archive:
        member_name = "passblur-gfx-input/files/libsurfaceflinger.so"
        member = archive.getmember(member_name)
        with archive.extractfile(member) as stream:
            binary = stream.read()
    failures = verify(binary, manifest)
    if failures:
        raise SystemExit("FAIL: " + "; ".join(failures))
    print(
        f"PASS: archive={actual_asset_sha} binary={sha256(binary)} "
        f"functions={len(manifest['functions'])}"
    )


if __name__ == "__main__":
    main()
