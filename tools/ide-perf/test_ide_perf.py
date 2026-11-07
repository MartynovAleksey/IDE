#!/usr/bin/env python3
"""Self-check for ide_perf.py parsers and report on synthetic data: python3 tools/ide-perf/test_ide_perf.py"""
import datetime
import json
import sys
import tempfile
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).parent))
import ide_perf  # noqa: E402

with tempfile.TemporaryDirectory() as tmp:
  tmp = Path(tmp)
  log = tmp / "log"
  (log / "indexing-diagnostic/p.1").mkdir(parents=True)
  (log / "idea.log").write_text(
    "2026-11-07 00:02:02,241 [     74]   INFO - #c.i.A - fine\n"
    "2026-11-07 00:02:03,000 [    900]  ERROR - #c.i.B - boom\n"
    "java.lang.RuntimeException: boom\n"
    "2026-11-07 00:02:04,000 [   1900]  ERROR - #c.i.B - boom\n"
    "2026-11-07 00:02:05,000 [   2900]   WARN - #c.i.C - careful\n")
  errors, warns, top = ide_perf.parse_logs(log)
  assert (errors, warns) == (2, 1), (errors, warns)
  assert top.most_common(1)[0] == ("#c.i.B - boom", 2), top

  (tmp / "startup.json").write_text('{"totalDuration": 1500, "items": [{"n": "a", "d": 10}, {"n": "a", "d": 5}]}stale tail}')
  assert ide_perf.parse_startup(tmp / "startup.json") == (1500, {"a": 15})
  assert ide_perf.parse_startup(tmp / "missing.json") == (None, {})

  def diag(name, kind, wall_ns, pause_ns, end, files=0):
    (log / "indexing-diagnostic/p.1" / name).write_text(json.dumps({"type": kind, "projectIndexingActivityHistory": {
      "times": {"totalWallTimeWithPauses": wall_ns, "wallTimeOnPause": pause_ns, "updatingEnd": end},
      "fileCount": {"numberOfFilesIndexedWithLoadingContent": files}}}))
  diag("diagnostic-1.json", "Scanning", 900_000_000, 100_000_000, "2026-11-06T21:00:01.000Z[UTC]")  # earlier than the Moscow one below, though it sorts later as a string
  diag("diagnostic-2.json", "DumbIndexing", 500_000_000, 0, "2026-11-07T00:00:03.000+03:00[Europe/Moscow]", files=42)
  launch = datetime.datetime(2026, 11, 6, 21, 0, 0, tzinfo=datetime.timezone.utc)
  scanning, indexing, files, to_smart = ide_perf.parse_indexing(log, launch)
  assert (round(scanning), round(indexing), files, round(to_smart)) == (800, 500, 42, 3000), (scanning, indexing, files, to_smart)

  it = {"status": "ok", "wall_ms": 9000, "startup_ms": 1500, "startup_phases": {"a": 15}, "scanning_ms": 800,
        "indexing_ms": 500, "indexed_files": 42, "to_smart_ms": 3000, "errors": 2, "warns": 1, "heap_used_mb": 300,
        "top_errors": [["#c.i.B - <boom>", 2]]}
  run = {"label": "A", "ide": {}, "ide_path": "/x", "project": "/p", "host": "h", "os": "o", "date": "d", "iterations": [it]}
  slower = dict(run, label="B", iterations=[dict(it, startup_ms=3000)])
  for name, r in (("a.json", run), ("b.json", slower)):
    (tmp / name).write_text(json.dumps(r))
  ide_perf.cmd_report(SimpleNamespace(results=[tmp / "a.json", tmp / "b.json"], out=tmp / "r.html"))
  page = (tmp / "r.html").read_text()
  assert "<td class='worse'>+100.0%</td>" in page, "startup delta"
  assert "&lt;boom&gt;" in page, "log text must be escaped"

print("ok")
