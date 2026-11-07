#!/usr/bin/env python3
"""IDE performance comparison: startup, indexing and log errors.

  run    -- launch an IDE N times on a project with fresh caches, write <out>.json
  report -- build an HTML report from one or more <out>.json (2+ runs are overlaid)

Relies only on what the IDE already writes:
  idea.log.perf.stats.file      -> startup report (StartUpPerformanceReporter)
  log/indexing-diagnostic/*     -> scanning / dumb indexing times (IndexDiagnosticDumper)
  idea.log.exit.metrics.file    -> heap at exit (performanceTesting plugin)
  log/idea*.log                 -> ERROR / WARN lines
and on the bundled performanceTesting plugin (testscript.filename) to wait for smart mode and exit.
"""
import argparse
import collections
import datetime
import html
import json
import os
import platform
import re
import shlex
import shutil
import statistics
import subprocess
import sys
import time
from pathlib import Path

SCRIPT = "%waitForSmart\n%exitApp\n"
LOG_LINE = re.compile(r"^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d,\d+ \[\s*\d+\]\s+(ERROR|WARN)\s+-\s+(\S+)\s+-\s?(.*)$")


# ---------------------------------------------------------------- run

def find_product_info(ide: Path) -> Path:
  for p in (ide / "Contents/Resources/product-info.json", ide / "product-info.json", ide / "Resources/product-info.json"):
    if p.is_file():
      return p
  sys.exit(f"product-info.json not found in {ide}")


def launcher_for(ide: Path):
  info_file = find_product_info(ide)
  info = json.loads(info_file.read_text())
  os_name = {"Darwin": "macOS", "Linux": "Linux", "Windows": "Windows"}[platform.system()]
  launches = [l for l in info["launch"] if l.get("os", os_name) == os_name] or info["launch"]
  launcher = (info_file.parent / launches[0]["launcherPath"]).resolve()
  if not (ide / "plugins/performanceTesting").exists() and not (ide / "Contents/plugins/performanceTesting").exists():
    print(f"WARNING: performanceTesting plugin not found in {ide}: the IDE will not exit by itself, run ends on --timeout")
  return launcher, info


def parse_startup(path: Path):
  if not path.is_file():
    return None, {}
  # the file is opened without TRUNCATE; raw_decode ignores a possible stale tail
  data, _ = json.JSONDecoder().raw_decode(path.read_text(errors="replace"))
  phases = {}
  for item in data.get("items", []):
    phases[item["n"]] = phases.get(item["n"], 0) + item["d"]
  return data.get("totalDuration"), phases


def parse_indexing(log_dir: Path, launch_utc: datetime.datetime):
  scanning = indexing = 0.0
  files = 0
  last_end = None
  for f in sorted((log_dir / "indexing-diagnostic").glob("*/diagnostic-*.json")):
    try:
      d = json.loads(f.read_text())
    except (OSError, ValueError):
      continue
    hist = d.get("projectIndexingActivityHistory", {})
    t = hist.get("times", {})
    wall = (t.get("totalWallTimeWithPauses", 0) - t.get("wallTimeOnPause", 0)) / 1e6
    if d.get("type") == "DumbIndexing":
      indexing += wall
      files += hist.get("fileCount", {}).get("numberOfFilesIndexedWithLoadingContent", 0)
    else:
      scanning += wall
    if t.get("updatingEnd"):
      end = datetime.datetime.fromisoformat(t["updatingEnd"].split("[")[0])  # "...+03:00[Europe/Moscow]"
      last_end = end if last_end is None else max(last_end, end)
  to_smart = (last_end - launch_utc).total_seconds() * 1000 if last_end else None
  return scanning, indexing, files, to_smart


def parse_logs(log_dir: Path):
  counts = collections.Counter()
  errors = collections.Counter()
  for f in sorted(log_dir.glob("idea*.log")):
    with f.open(errors="replace") as fh:
      for line in fh:
        m = LOG_LINE.match(line)
        if m:
          counts[m.group(1)] += 1
          if m.group(1) == "ERROR":
            errors[f"{m.group(2)} - {m.group(3).strip()[:200]}"] += 1
  return counts["ERROR"], counts["WARN"], errors


def run_iteration(launcher: Path, project: Path, work: Path, config_from, timeout: int, keep: bool):
  if work.exists():
    shutil.rmtree(work)
  dirs = {k: work / k for k in ("config", "system", "log", "plugins")}
  for d in dirs.values():
    d.mkdir(parents=True)
  if config_from:
    shutil.copytree(config_from, dirs["config"], dirs_exist_ok=True)
  (work / "script.txt").write_text(SCRIPT)
  props = {
    "idea.config.path": dirs["config"], "idea.system.path": dirs["system"],
    "idea.log.path": dirs["log"], "idea.plugins.path": dirs["plugins"],
    "testscript.filename": work / "script.txt",
    # otherwise the plugin waits for all status-bar progress to vanish, which may never happen; %waitForSmart waits for indexing itself
    "performance.execute.script.right.after.ide.opened": "true",
    "idea.log.perf.stats.file": work / "startup.json",
    "idea.log.exit.metrics.file": work / "exit.json",
    "jb.consents.confirmation.enabled": "false",
    "jb.privacy.policy.text": "<!--999.999-->",
    "idea.trust.all.projects": "true",
    "ide.show.tips.on.startup.default.value": "false",
    "ide.no.platform.update": "true",
    "ide.experimental.ui.onboarding": "false",  # modal "Meet the new UI" dialog on a fresh config
  }
  env = dict(os.environ)
  # IJ_JAVA_OPTIONS is appended by the launcher after the bundled .vmoptions
  env["IJ_JAVA_OPTIONS"] = " ".join(shlex.quote(f"-D{k}={v}") for k, v in props.items())

  launch_utc = datetime.datetime.now(datetime.timezone.utc)
  started = time.monotonic()
  with (work / "stdout.log").open("w") as out:
    proc = subprocess.Popen([str(launcher), str(project)], env=env, stdout=out, stderr=subprocess.STDOUT)
    status = "ok"
    try:
      proc.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
      status = "timeout"
      proc.kill()
      proc.wait()
  wall = (time.monotonic() - started) * 1000
  if status == "ok" and proc.returncode != 0:
    status = f"exit {proc.returncode}"

  startup_total, phases = parse_startup(work / "startup.json")
  scanning, indexing, files, to_smart = parse_indexing(dirs["log"], launch_utc)
  errors, warns, error_lines = parse_logs(dirs["log"])
  heap = None
  if (work / "exit.json").is_file():
    heap = json.loads((work / "exit.json").read_text()).get("memory", {}).get("usedMb")
  if not keep:
    shutil.rmtree(dirs["system"], ignore_errors=True)  # indexes are large; logs stay for debugging
  return {
    "status": status, "wall_ms": round(wall),
    "startup_ms": startup_total, "startup_phases": phases,
    "scanning_ms": round(scanning), "indexing_ms": round(indexing), "indexed_files": files,
    "to_smart_ms": round(to_smart) if to_smart is not None else None,
    "errors": errors, "warns": warns, "heap_used_mb": heap,
    "top_errors": error_lines.most_common(20), "work_dir": str(work),
  }


def cmd_run(a):
  ide = Path(a.ide).expanduser().resolve()
  project = Path(a.project).expanduser().resolve()
  launcher, info = launcher_for(ide)
  out = Path(a.out)
  out.parent.mkdir(parents=True, exist_ok=True)
  label = a.label or f"{info.get('name')} {info.get('buildNumber')}"
  result = {
    "label": label, "ide": {k: info.get(k) for k in ("name", "version", "buildNumber", "productCode")},
    "ide_path": str(ide), "project": str(project), "host": platform.node(),
    "os": f"{platform.system()} {platform.release()} {platform.machine()}",
    "date": datetime.datetime.now().isoformat(timespec="seconds"), "iterations": [],
  }
  work_root = out.with_suffix("")  # results/a.json -> results/a/iter-N
  for i in range(1, a.iterations + 1):
    print(f"[{label}] iteration {i}/{a.iterations} ...", flush=True)
    it = run_iteration(launcher, project, work_root / f"iter-{i}", a.config_from, a.timeout, a.keep_caches)
    print(f"  status={it['status']} startup={it['startup_ms']}ms scanning={it['scanning_ms']}ms "
          f"indexing={it['indexing_ms']}ms to_smart={it['to_smart_ms']}ms errors={it['errors']}", flush=True)
    result["iterations"].append(it)
    out.write_text(json.dumps(result, indent=2, ensure_ascii=False))  # partial results survive a crash
  print(f"saved {out}")


# ---------------------------------------------------------------- report

METRICS = [  # key, title, unit, lower_is_better
  ("startup_ms", "Старт IDE (до окончания startup-активностей)", "мс", True),
  ("scanning_ms", "Сканирование файлов", "мс", True),
  ("indexing_ms", "Индексация (dumb mode)", "мс", True),
  ("to_smart_ms", "От запуска до smart mode", "мс", True),
  ("indexed_files", "Проиндексировано файлов", "шт", None),
  ("errors", "ERROR в логах", "шт", True),
  ("warns", "WARN в логах", "шт", True),
  ("heap_used_mb", "Heap при выходе", "МБ", True),
]
COLORS = ["#2a78d6", "#e8743b", "#19a979", "#945ecf", "#d64e75", "#888888"]


def values(run, key):
  return [it[key] for it in run["iterations"] if it.get(key) is not None]


def median(xs):
  return statistics.median(xs) if xs else None


def fmt(v):
  return "—" if v is None else f"{v:,.0f}".replace(",", " ")


def summary_table(runs):
  head = "".join(f"<th>{html.escape(r['label'])}</th>" for r in runs)
  if len(runs) > 1:
    head += "".join(f"<th>Δ {html.escape(r['label'])}</th>" for r in runs[1:])
  rows = []
  for key, title, unit, lower in METRICS:
    meds = [median(values(r, key)) for r in runs]
    cells = []
    for r, m in zip(runs, meds):
      vs = values(r, key)
      spread = f"<div class=sub>{fmt(min(vs))} … {fmt(max(vs))}</div>" if len(vs) > 1 else ""
      cells.append(f"<td>{fmt(m)}{spread}</td>")
    for m in meds[1:]:
      base = meds[0]
      if base in (None, 0) or m is None:
        cells.append("<td>—</td>")
        continue
      pct = (m - base) / base * 100
      cls = "" if lower is None or abs(pct) < 3 else ("better" if (pct < 0) == lower else "worse")
      cells.append(f"<td class='{cls}'>{pct:+.1f}%</td>")
    rows.append(f"<tr><th class=left>{title}, {unit}</th>{''.join(cells)}</tr>")
  return f"<table><tr><th class=left>Метрика (медиана, min…max)</th>{head}</tr>{''.join(rows)}</table>"


def charts_data(runs):
  max_iters = max(len(r["iterations"]) for r in runs)
  labels = [f"#{i}" for i in range(1, max_iters + 1)]

  def per_iteration(key):
    return [{"label": r["label"], "data": [it.get(key) for it in r["iterations"]],
             "borderColor": COLORS[i % len(COLORS)], "backgroundColor": COLORS[i % len(COLORS)]}
            for i, r in enumerate(runs)]

  time_keys = [m for m in METRICS if m[2] == "мс"]
  medians = {"labels": [m[1] for m in time_keys],
             "datasets": [{"label": r["label"], "data": [median(values(r, m[0])) for m in time_keys],
                           "backgroundColor": COLORS[i % len(COLORS)]} for i, r in enumerate(runs)]}

  phase_meds = []
  for r in runs:
    acc = collections.defaultdict(list)
    for it in r["iterations"]:
      for name, d in (it.get("startup_phases") or {}).items():
        acc[name].append(d)
    phase_meds.append({k: statistics.median(v) for k, v in acc.items()})
  names = sorted({n for p in phase_meds for n in p}, key=lambda n: -max(p.get(n, 0) for p in phase_meds))[:20]
  phases = {"labels": names,
            "datasets": [{"label": r["label"], "data": [p.get(n) for n in names],
                          "backgroundColor": COLORS[i % len(COLORS)]} for i, (r, p) in enumerate(zip(runs, phase_meds))]}
  return {
    "medians": medians, "phases": phases,
    "lines": {key: {"labels": labels, "datasets": per_iteration(key)}
              for key in ("startup_ms", "to_smart_ms", "scanning_ms", "indexing_ms", "errors", "warns")},
  }


def run_details(r):
  info = r["ide"]
  rows = "".join(
    f"<tr><td>{i}</td><td>{html.escape(it['status'])}</td><td>{fmt(it['startup_ms'])}</td><td>{fmt(it['scanning_ms'])}</td>"
    f"<td>{fmt(it['indexing_ms'])}</td><td>{fmt(it['to_smart_ms'])}</td><td>{fmt(it['indexed_files'])}</td>"
    f"<td>{fmt(it['errors'])}</td><td>{fmt(it['warns'])}</td><td>{fmt(it['wall_ms'])}</td></tr>"
    for i, it in enumerate(r["iterations"], 1))
  errors = collections.Counter()
  for it in r["iterations"]:
    for msg, n in it.get("top_errors", []):
      errors[msg] += n
  err_rows = "".join(f"<tr><td>{n}</td><td class=left><code>{html.escape(m)}</code></td></tr>" for m, n in errors.most_common(15))
  return f"""
<h3>{html.escape(r['label'])}</h3>
<p class=sub>{html.escape(str(info.get('name')))} {html.escape(str(info.get('version')))} ({html.escape(str(info.get('buildNumber')))}) ·
{html.escape(r['ide_path'])} · проект {html.escape(r['project'])} · {html.escape(r['host'])}, {html.escape(r['os'])} · {html.escape(r['date'])}</p>
<table><tr><th>#</th><th>статус</th><th>старт</th><th>сканирование</th><th>индексация</th><th>до smart</th><th>файлов</th><th>ERROR</th><th>WARN</th><th>весь прогон</th></tr>{rows}</table>
<h4>Частые ERROR (сумма по итерациям)</h4>
{f'<table><tr><th>раз</th><th class=left>логгер - сообщение</th></tr>{err_rows}</table>' if err_rows else '<p class=sub>ошибок нет</p>'}
"""


def cmd_report(a):
  runs = [json.loads(Path(p).read_text()) for p in a.results]
  title = " vs ".join(r["label"] for r in runs)
  data = json.dumps(charts_data(runs), ensure_ascii=False)
  page = f"""<!doctype html>
<html lang=ru><head><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<title>Производительность IDE: {html.escape(title)}</title>
<script src="https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.1/chart.umd.min.js"></script>
<style>
body{{font:14px -apple-system,system-ui,sans-serif;margin:0 auto;max-width:1200px;padding:16px;color:#222;background:#fff}}
table{{border-collapse:collapse;margin:8px 0 20px;width:100%}}
th,td{{border-bottom:1px solid #e3e3e3;padding:6px 10px;text-align:right;vertical-align:top}}
.left{{text-align:left}} .sub{{color:#777;font-size:12px}} code{{font-size:12px;word-break:break-all}}
.better{{color:#11804f;font-weight:600}} .worse{{color:#c62828;font-weight:600}}
.grid{{display:grid;grid-template-columns:repeat(auto-fit,minmax(460px,1fr));gap:24px}}
.card{{border:1px solid #e3e3e3;border-radius:8px;padding:12px}} h2{{margin-top:32px}}
</style></head><body>
<h1>Производительность IDE</h1>
<p class=sub>{html.escape(title)} · сформирован {datetime.datetime.now():%Y-%m-%d %H:%M}.
Каждая итерация — холодный старт с пустыми config/system (индексы с нуля). Δ — относительно первого прогона; зелёный — лучше, красный — хуже (порог 3%).</p>
<h2>Сводка</h2>
{summary_table(runs)}
<h2>Графики</h2>
<div class=grid>
<div class=card><h4>Медианы времён, мс</h4><canvas id=medians></canvas></div>
<div class=card><h4>Старт IDE по итерациям, мс</h4><canvas id=startup_ms></canvas></div>
<div class=card><h4>От запуска до smart mode по итерациям, мс</h4><canvas id=to_smart_ms></canvas></div>
<div class=card><h4>Индексация по итерациям, мс</h4><canvas id=indexing_ms></canvas></div>
<div class=card><h4>Сканирование по итерациям, мс</h4><canvas id=scanning_ms></canvas></div>
<div class=card><h4>ERROR в логах по итерациям</h4><canvas id=errors></canvas></div>
<div class=card><h4>WARN в логах по итерациям</h4><canvas id=warns></canvas></div>
</div>
<div class=card style="margin-top:24px"><h4>Фазы старта (медиана, мс, топ-20)</h4><canvas id=phases height=140></canvas></div>
<h2>Детали прогонов</h2>
{''.join(run_details(r) for r in runs)}
<script>
const D = {data};
const opts = {{responsive:true, interaction:{{mode:'index',intersect:false}}, scales:{{y:{{beginAtZero:true}}}}}};
new Chart(medians, {{type:'bar', data:D.medians, options:opts}});
for (const [id, d] of Object.entries(D.lines)) new Chart(document.getElementById(id), {{type:'line', data:d, options:opts}});
new Chart(phases, {{type:'bar', data:D.phases, options:{{...opts, indexAxis:'y', scales:{{x:{{beginAtZero:true}}}}}}}});
</script>
</body></html>"""
  Path(a.out).write_text(page)
  print(f"saved {a.out}")


def main():
  p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
  sub = p.add_subparsers(dest="cmd", required=True)
  r = sub.add_parser("run", help="measure one IDE")
  r.add_argument("--ide", required=True, help="IDE home: /Applications/X.app or unpacked dist")
  r.add_argument("--project", required=True, help="project to open")
  r.add_argument("--out", required=True, help="result json, e.g. results/oss.json")
  r.add_argument("--label", help="name in the report (default: product name + build)")
  r.add_argument("-n", "--iterations", type=int, default=3)
  r.add_argument("--timeout", type=int, default=1800, help="seconds per iteration")
  r.add_argument("--config-from", help="config dir copied into each iteration (license, settings)")
  r.add_argument("--keep-caches", action="store_true", help="keep system dir (indexes) after each iteration")
  r.set_defaults(func=cmd_run)
  rep = sub.add_parser("report", help="html report from one or more result json files")
  rep.add_argument("results", nargs="+")
  rep.add_argument("-o", "--out", default="ide-perf-report.html")
  rep.set_defaults(func=cmd_report)
  a = p.parse_args()
  a.func(a)


if __name__ == "__main__":
  main()
