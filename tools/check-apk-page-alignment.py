#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Check packaged 64-bit ELF LOAD/RELRO alignment and APK ZIP alignment."""
import argparse
from pathlib import Path
import struct
import subprocess
import zipfile

PAGE = 16384


def check_elf(data, label):
    if data[:6] != b"\x7fELF\x02\x01":
        raise ValueError(f"{label}: expected little-endian ELF64")
    offset = struct.unpack_from("<Q", data, 32)[0]
    entry_size, count = struct.unpack_from("<HH", data, 54)
    if entry_size < 56 or not count:
        raise ValueError(f"{label}: invalid program header table")
    headers = [struct.unpack_from("<IIQQQQQQ", data, offset + i * entry_size)
               for i in range(count)]
    loads = [h for h in headers if h[0] == 1]
    if not loads:
        raise ValueError(f"{label}: no LOAD segments")
    for _, flags, off, address, _, file_size, memory_size, alignment in loads:
        if alignment < PAGE or alignment & (alignment - 1) or (address - off) % PAGE:
            raise ValueError(f"{label}: LOAD not 16 KiB aligned")
    for h in headers:
        if h[0] != 0x6474e552:  # PT_GNU_RELRO
            continue
        end = h[3] + h[6]
        for load in loads:
            if load[1] & 2 and load[3] <= h[3] < load[3] + load[6]:
                if end % PAGE and end != load[3] + load[6]:
                    raise ValueError(f"{label}: RELRO shares its trailing 16 KiB page with writable data")
    print(f"{label}: LOAD and RELRO OK", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--zipalign", required=True)
    parser.add_argument("apks", nargs="+", type=Path)
    args = parser.parse_args()
    checked = 0
    for apk in args.apks:
        subprocess.run([args.zipalign, "-c", "-P", "16", "4", str(apk)], check=True)
        libraries = 0
        with zipfile.ZipFile(apk) as archive:
            for name in archive.namelist():
                if name.startswith(("lib/arm64-v8a/", "lib/x86_64/")) and name.endswith(".so"):
                    check_elf(archive.read(name), f"{apk.name}:{name}")
                    checked += 1
                    libraries += 1
        if not libraries and ("universal" in apk.name or "arm64-v8a" in apk.name or "x86_64" in apk.name):
            raise ValueError(f"{apk}: expected 64-bit native libraries")
    if not checked:
        raise ValueError("No 64-bit libraries checked")
    print(f"Verified {len(args.apks)} APKs and {checked} 64-bit libraries")


if __name__ == "__main__":
    main()
