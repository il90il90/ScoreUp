#!/usr/bin/env python3
"""Gradle's cache stores module files under their metadata `name` (e.g. material3-release.aar),
but a Maven repo serves them at their `url` (material3-android-1.3.1.aar). Link url -> name."""
import json, os, sys
root = sys.argv[1]
fixed = 0
for d, _, files in os.walk(root):
    for f in files:
        if not f.endswith(".module"):
            continue
        try:
            meta = json.load(open(os.path.join(d, f)))
        except Exception:
            continue
        for v in meta.get("variants", []):
            for af in v.get("files", []):
                name, url = af.get("name"), af.get("url")
                if not name or not url or "/" in url:
                    continue
                src, dst = os.path.join(d, name), os.path.join(d, url)
                if os.path.exists(src) and not os.path.exists(dst):
                    os.link(src, dst); fixed += 1
print(f"linked {fixed} files")
