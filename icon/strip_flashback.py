"""Cuts an installed Flashback jar down to the classes needed to compile against it.

The released jar carries ffmpeg and a pile of native blobs and weighs two hundred megabytes, which
Loom would then have to remap on every clean build. Only `com/moulberry/**` and the mod metadata are
kept; the result stays on this machine and is not redistributable.

    python icon/strip_flashback.py "C:/.../Flashback-0.39.1-for-MC1.21.8.jar"
"""

import re
import sys
import zipfile
from pathlib import Path

KEEP = re.compile(r"^(com/moulberry/|fabric\.mod\.json$|flashback\.mixins\.json$|.*\.accesswidener$)")


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)

    source = Path(sys.argv[1])
    version = re.search(r"Flashback-([0-9.]+)-", source.name)
    out = Path(__file__).resolve().parent.parent / "libs" / (
        "flashback-%s-api.jar" % (version.group(1) if version else "unknown")
    )

    with zipfile.ZipFile(source) as src, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as dst:
        kept = 0
        for entry in src.namelist():
            if KEEP.match(entry):
                dst.writestr(entry, src.read(entry))
                kept += 1

    print("written %s (%d entries, %.1f MB)" % (out, kept, out.stat().st_size / 1024 / 1024))
    print("поставь это имя в gradle.properties -> flashback_jar")


if __name__ == "__main__":
    main()
