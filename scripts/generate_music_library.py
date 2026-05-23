#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from urllib.parse import quote


AUDIO_EXTENSIONS = {".ogg", ".wav"}


def slug(value: str) -> str:
    value = value.lower()
    value = re.sub(r"[^a-z0-9_.-]+", "_", value)
    value = re.sub(r"_+", "_", value)
    return value.strip("_")


def stable_track_id(name: str) -> str:
    digest = hashlib.sha1(name.encode("utf-8")).hexdigest()[:8]
    return f"track_{digest}"


def load_json(path: Path) -> dict:
    if not path.exists():
        return {}
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def existing_by_file(library: dict) -> dict[str, dict]:
    result = {}
    for track in library.get("tracks", []):
        file_name = track.get("file")
        if isinstance(file_name, str):
            result[file_name] = track
    return result


def metadata_by_file(metadata: dict) -> dict[str, dict]:
    tracks = metadata.get("tracks", {})
    if isinstance(tracks, dict):
        return {str(key): value for key, value in tracks.items() if isinstance(value, dict)}
    if isinstance(tracks, list):
        return {
            str(value["file"]): value
            for value in tracks
            if isinstance(value, dict) and isinstance(value.get("file"), str)
        }
    return {}


def build_track(audio_path: Path, library_dir: Path, existing: dict, metadata: dict, mode: str, base_url: str) -> dict:
    file_name = audio_path.name
    stem = audio_path.stem
    previous = existing.get(file_name, {})
    override = metadata.get(file_name, {})

    track_id = override.get("id") or previous.get("id") or slug(stem) or stable_track_id(file_name)
    track_id = slug(track_id) or stable_track_id(file_name)

    title = override.get("title") or previous.get("title") or stem
    volume = float(override.get("volume", previous.get("volume", 1.0)))
    loop = bool(override.get("loop", previous.get("loop", False)))

    track = {
        "id": track_id,
        "title": title,
        "volume": volume,
        "loop": loop,
    }

    if mode == "github":
        track["url"] = f"{base_url.rstrip('/')}/{quote(file_name)}"
    else:
        track["file"] = file_name

    return track


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate musiclibraries/library.json from audio files.")
    parser.add_argument("--library-dir", default="musiclibraries", help="Directory containing music files.")
    parser.add_argument("--mode", choices=("local", "github"), default="local", help="Use local file paths or GitHub raw URLs.")
    parser.add_argument("--base-url", default="", help="Base raw URL for --mode github.")
    args = parser.parse_args()

    library_dir = Path(args.library_dir)
    library_json = library_dir / "library.json"
    metadata_json = library_dir / "metadata.json"

    if args.mode == "github" and not args.base_url:
        raise SystemExit("--base-url is required when --mode github")

    existing = existing_by_file(load_json(library_json))
    metadata = metadata_by_file(load_json(metadata_json))
    audio_files = sorted(
        path
        for path in library_dir.iterdir()
        if path.is_file() and path.suffix.lower() in AUDIO_EXTENSIONS
    )

    tracks = [
        build_track(path, library_dir, existing, metadata, args.mode, args.base_url)
        for path in audio_files
    ]
    library = {"tracks": tracks}

    library_dir.mkdir(parents=True, exist_ok=True)
    with library_json.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(library, handle, ensure_ascii=True, indent=2)
        handle.write("\n")


if __name__ == "__main__":
    main()
