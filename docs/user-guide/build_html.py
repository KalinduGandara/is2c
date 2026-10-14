#!/usr/bin/env python3
"""Builds index.html from USER_GUIDE.md (the single source of the user guide).

    python3 docs/user-guide/build_html.py

Needs Python-Markdown (`pip install markdown`). The HTML references the screenshots in images/, so share the
whole docs/user-guide folder (or zip it). Screenshots are regenerated with
`cd frontend && npx playwright test -c playwright.guide.config.ts`.
"""
import html
import re
import sys
from pathlib import Path

import markdown
from markdown.extensions.toc import TocExtension

HERE = Path(__file__).resolve().parent
SRC = HERE / "USER_GUIDE.md"
OUT = HERE / "index.html"


def slugify(value: str, separator: str = "-") -> str:
    """GitHub-compatible heading anchors, so the same links work on GitHub and in the HTML."""
    value = re.sub(r"<[^>]+>", "", value).strip().lower()
    value = re.sub(r"[^\w\- ]", "", value, flags=re.UNICODE)
    return value.replace(" ", separator)


# Diagrams drawn natively (the Markdown uses Mermaid, which GitHub renders).
def chip(text: str, kind: str = "") -> str:
    return f'<span class="chip {kind}">{html.escape(text)}</span>'


ARROW = '<span class="arrow" aria-hidden="true">→</span>'
DIAGRAMS = [
    # punch → payslip
    '<figure class="diagram" aria-label="From punch to payslip">'
    '<div class="flow">'
    '<div class="stack">' + chip("Device punches / CSV") + chip("Shifts, rosters, rotations") + chip("Holidays")
    + chip("Approved leave") + '</div>' + ARROW
    + chip("Daily attendance", "strong") + ARROW + chip("Overtime approval & corrections") + ARROW
    + '<div class="stack">' + chip("Salary structures") + chip("Payroll run", "strong") + '</div>' + ARROW
    + chip("Review → Approve → Finalize") + ARROW + chip("Payslips, exports, reports", "strong")
    + '</div><figcaption>From punch to payslip</figcaption></figure>',
    # payroll lifecycle
    '<figure class="diagram" aria-label="Payroll period lifecycle">'
    '<div class="flow">' + chip("Open") + '<span class="arrow">Process →</span>' + chip("Draft", "strong")
    + '<span class="arrow">Submit for review →</span>' + chip("In review") + '<span class="arrow">Approve →</span>'
    + chip("Approved") + '<span class="arrow">Finalize →</span>' + chip("Finalized", "done") + '</div>'
    '<p class="flow-note">Reprocess keeps the period in <b>Draft</b>. <b>Send back</b> or a rejection returns an '
    'in-review period to <b>Draft</b>.</p><figcaption>Payroll period lifecycle</figcaption></figure>',
]

ALERTS = {"NOTE": ("note", "Note"), "TIP": ("tip", "Tip"), "IMPORTANT": ("important", "Important"),
          "WARNING": ("warning", "Warning")}


def preprocess(md: str) -> str:
    # Mermaid blocks → placeholders, filled after conversion.
    count = 0

    def mermaid(_m):
        nonlocal count
        count += 1
        return f"\n<!--DIAGRAM{count - 1}-->\n"

    md = re.sub(r"```mermaid\n.*?```", mermaid, md, flags=re.S)

    # Side-by-side screenshot pairs.
    md = re.sub(r'<p class="pair">(.*?)</p>', r'<div class="pair" markdown="1">\1</div>', md, flags=re.S)

    # GitHub alerts (> [!NOTE] …) → callout blocks rendered as Markdown.
    out, lines, i = [], md.split("\n"), 0
    while i < len(lines):
        m = re.match(r"^> \[!(NOTE|TIP|IMPORTANT|WARNING)\]\s*$", lines[i])
        if not m:
            out.append(lines[i])
            i += 1
            continue
        kind, title = ALERTS[m.group(1)]
        body = []
        i += 1
        while i < len(lines) and lines[i].startswith(">"):
            body.append(lines[i][2:] if lines[i].startswith("> ") else lines[i][1:])
            i += 1
        out += [f'<div class="callout {kind}" markdown="1">', f'<p class="callout-title">{title}</p>', "", *body, "", "</div>"]
    md = "\n".join(out)

    return md


def postprocess(body: str) -> str:
    figure_no = 0

    def figure(m):
        nonlocal figure_no
        figure_no += 1
        src, alt = m.group("src"), html.unescape(m.group("alt"))
        return (f'<figure><a href="{src}" target="_blank" rel="noopener"><img src="{src}" alt="{html.escape(alt)}" loading="lazy"></a>'
                f'<figcaption><span>Figure {figure_no}.</span> {html.escape(alt)}</figcaption></figure>')

    img = r'<img alt="(?P<alt>[^"]*)" src="(?P<src>[^"]*)" />'
    # a paragraph holding one image (or a pair's paragraph holding two) becomes figures
    body = re.sub(r"<p>((?:\s*" + img.replace("?P<alt>", "?:").replace("?P<src>", "?:") + r"\s*)+)</p>",
                  lambda m: re.sub(img, figure, m.group(1)), body)
    for n, diagram in enumerate(DIAGRAMS):
        body = body.replace(f"<!--DIAGRAM{n}-->", diagram)
    body = re.sub(r"<table>", '<div class="table-wrap"><table>', body).replace("</table>", "</table></div>")
    # tables written with an empty header row (`| | |`) are key/value lists: drop the empty header
    body = re.sub(r"<thead>\s*<tr>(?:\s*<th></th>)+\s*</tr>\s*</thead>", "", body)
    return body


def check(body: str, toc_ids: set[str]) -> None:
    problems = []
    for anchor in set(re.findall(r'href="#([^"]+)"', body)):
        if anchor not in toc_ids:
            problems.append(f"broken link #{anchor}")
    for src in set(re.findall(r'src="(images/[^"]+)"', body)):
        if not (HERE / src).exists():
            problems.append(f"missing image {src}")
    if problems:
        sys.exit("build_html: " + "; ".join(sorted(problems)))


def toc_html(tokens) -> str:
    items = []
    for t in tokens:
        if t["level"] != 2:
            continue
        children = "".join(f'<li><a href="#{c["id"]}">{c["name"]}</a></li>' for c in t["children"] if c["level"] == 3)
        sub = f"<ul>{children}</ul>" if children else ""
        items.append(f'<li><a href="#{t["id"]}">{t["name"]}</a>{sub}</li>')
    return "<ul>" + "".join(items) + "</ul>"


def main() -> None:
    md = markdown.Markdown(extensions=[
        "tables", "fenced_code", "md_in_html", "sane_lists", "attr_list",
        TocExtension(slugify=slugify, toc_depth="2-3"),
    ])
    body = md.convert(preprocess(SRC.read_text(encoding="utf-8")))
    tokens = md.toc_tokens  # type: ignore[attr-defined]
    ids = set(re.findall(r'id="([^"]+)"', body))
    body = postprocess(body)
    check(body, ids)
    # the first h1 and the edition table form the cover; everything after "How to use this guide" is content
    page = TEMPLATE.replace("{{TOC}}", toc_html(tokens[0]["children"] if tokens and tokens[0]["level"] == 1 else tokens))
    page = page.replace("{{BODY}}", body)
    OUT.write_text(page, encoding="utf-8")
    print(f"wrote {OUT.relative_to(HERE.parent.parent)} ({len(page) // 1024} KB)")


TEMPLATE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Premium HRM User Guide</title>
<meta name="description" content="Premium HRM user guide: installation, setup, employees, attendance, rosters, leave, approvals, payroll, reports and self-service.">
<style>
:root {
  --bg: #f6f7fb; --surface: #ffffff; --text: #1d2130; --muted: #5b6275; --border: #e3e6ef;
  --brand: #4c4fe0; --brand-soft: #eef0ff; --code-bg: #f1f3f8; --shadow: 0 1px 2px rgba(16,24,40,.06), 0 4px 16px rgba(16,24,40,.06);
  --note: #3b82f6; --note-bg: #eef5ff; --tip: #0f9f6e; --tip-bg: #e9f8f1; --important: #8b5cf6; --important-bg: #f4efff;
  --warning: #d97706; --warning-bg: #fff6e8;
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    --bg: #11131a; --surface: #191c25; --text: #e7e9f0; --muted: #a2a8b8; --border: #2b3040;
    --brand: #8c8ffb; --brand-soft: #23264a; --code-bg: #20242f; --shadow: none;
    --note-bg: #16233a; --tip-bg: #12291f; --important-bg: #241d38; --warning-bg: #2d2313;
  }
}
:root[data-theme="dark"] {
  --bg: #11131a; --surface: #191c25; --text: #e7e9f0; --muted: #a2a8b8; --border: #2b3040;
  --brand: #8c8ffb; --brand-soft: #23264a; --code-bg: #20242f; --shadow: none;
  --note-bg: #16233a; --tip-bg: #12291f; --important-bg: #241d38; --warning-bg: #2d2313;
}
* { box-sizing: border-box; }
html { scroll-behavior: smooth; scroll-padding-top: 76px; }
body { margin: 0; background: var(--bg); color: var(--text); font: 16px/1.65 Inter, "Segoe UI", system-ui, -apple-system, Roboto, sans-serif; }
a { color: var(--brand); text-decoration: none; }
a:hover { text-decoration: underline; }
header.top { position: sticky; top: 0; z-index: 10; display: flex; align-items: center; gap: 12px; height: 60px; padding: 0 20px;
  background: color-mix(in srgb, var(--surface) 88%, transparent); backdrop-filter: blur(8px); border-bottom: 1px solid var(--border); }
header.top .logo { width: 32px; height: 32px; border-radius: 9px; background: linear-gradient(135deg, #6366f1, #8b5cf6); display: grid; place-items: center; color: #fff; font-weight: 800; }
header.top .name { font-weight: 700; }
header.top .ver { color: var(--muted); font-size: 14px; }
header.top .spacer { flex: 1; }
header.top button { border: 1px solid var(--border); background: var(--surface); color: var(--text); border-radius: 8px; height: 34px; padding: 0 12px; cursor: pointer; font: inherit; font-size: 14px; }
#menu-btn { display: none; }
.layout { display: grid; grid-template-columns: 300px minmax(0, 1fr); max-width: 1480px; margin: 0 auto; }
nav.toc { position: sticky; top: 60px; height: calc(100vh - 60px); overflow-y: auto; padding: 24px 16px 40px 20px; border-right: 1px solid var(--border); font-size: 14px; }
nav.toc ul { list-style: none; margin: 0; padding: 0; }
nav.toc > ul > li { margin: 2px 0; }
nav.toc > ul > li > a { display: block; padding: 5px 10px; border-radius: 7px; color: var(--text); font-weight: 600; }
nav.toc ul ul { margin: 2px 0 8px 10px; padding-left: 8px; border-left: 1px solid var(--border); display: none; }
nav.toc li.open > ul { display: block; }
nav.toc ul ul a { display: block; padding: 3px 8px; color: var(--muted); border-radius: 6px; }
nav.toc a.active { background: var(--brand-soft); color: var(--brand); }
nav.toc a:hover { text-decoration: none; background: var(--brand-soft); }
main { padding: 32px clamp(16px, 4vw, 64px) 80px; min-width: 0; }
article { max-width: 980px; }
h1 { font-size: clamp(30px, 4vw, 42px); line-height: 1.15; margin: 8px 0 6px; letter-spacing: -.02em; }
h1 + p { color: var(--muted); font-size: 18px; margin-top: 0; }
h2 { font-size: 28px; line-height: 1.25; margin: 64px 0 12px; padding-top: 12px; letter-spacing: -.01em; border-top: 1px solid var(--border); }
h3 { font-size: 20px; margin: 36px 0 8px; }
h2 + h3 { margin-top: 20px; }
p, ul, ol { margin: 10px 0; }
li { margin: 4px 0; }
hr { border: 0; border-top: 1px solid var(--border); margin: 32px 0; }
code { font: 13.5px/1.5 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; background: var(--code-bg); padding: 1px 6px; border-radius: 5px; }
pre { background: var(--code-bg); border: 1px solid var(--border); border-radius: 10px; padding: 14px 16px; overflow-x: auto; }
pre code { background: none; padding: 0; }
.table-wrap { overflow-x: auto; margin: 14px 0; border: 1px solid var(--border); border-radius: 10px; background: var(--surface); }
table { border-collapse: collapse; width: 100%; font-size: 14.5px; }
th, td { text-align: left; vertical-align: top; padding: 9px 12px; border-bottom: 1px solid var(--border); }
th { background: var(--code-bg); font-weight: 600; white-space: nowrap; }
tr:last-child td { border-bottom: 0; }
figure { margin: 18px 0 26px; }
figure img { display: block; width: 100%; height: auto; border: 1px solid var(--border); border-radius: 10px; box-shadow: var(--shadow); background: #fff; }
figcaption { margin-top: 8px; font-size: 13.5px; color: var(--muted); }
figcaption span { font-weight: 600; color: var(--text); }
.pair { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; align-items: start; }
.pair figure { margin: 10px 0; }
.callout { border: 1px solid var(--border); border-left: 4px solid var(--note); background: var(--note-bg); border-radius: 10px; padding: 10px 16px; margin: 18px 0; }
.callout > :last-child { margin-bottom: 4px; }
.callout-title { font-weight: 700; margin: 2px 0 2px; font-size: 14px; text-transform: uppercase; letter-spacing: .04em; color: var(--note); }
.callout.tip { border-left-color: var(--tip); background: var(--tip-bg); } .callout.tip .callout-title { color: var(--tip); }
.callout.important { border-left-color: var(--important); background: var(--important-bg); } .callout.important .callout-title { color: var(--important); }
.callout.warning { border-left-color: var(--warning); background: var(--warning-bg); } .callout.warning .callout-title { color: var(--warning); }
.callout figure { max-width: 520px; }
.diagram { border: 1px solid var(--border); border-radius: 12px; background: var(--surface); padding: 18px; }
.flow { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.stack { display: flex; flex-direction: column; gap: 6px; }
.chip { display: inline-block; padding: 6px 12px; border-radius: 8px; border: 1px solid var(--border); background: var(--code-bg); font-size: 14px; font-weight: 500; }
.chip.strong { background: var(--brand-soft); border-color: var(--brand); color: var(--brand); font-weight: 700; }
.chip.done { background: var(--tip-bg); border-color: var(--tip); color: var(--tip); font-weight: 700; }
.arrow { color: var(--muted); font-size: 14px; white-space: nowrap; }
.flow-note { font-size: 14px; color: var(--muted); margin: 12px 0 0; }
.diagram figcaption { margin-top: 12px; }
@media (max-width: 960px) {
  .layout { grid-template-columns: 1fr; }
  #menu-btn { display: inline-block; }
  nav.toc { position: fixed; top: 60px; left: 0; width: min(320px, 88vw); z-index: 9; background: var(--surface); transform: translateX(-100%); transition: transform .2s; box-shadow: var(--shadow); }
  body.menu-open nav.toc { transform: none; }
  .pair { grid-template-columns: 1fr; }
  header.top .ver { display: none; }
}
@media (max-width: 560px) {
  header.top { gap: 8px; padding: 0 12px; }
  header.top .lbl, #print-btn { display: none; }
}
@media print {
  @page { size: A4; margin: 16mm 14mm; }
  header.top, nav.toc { display: none; }
  .layout { display: block; }
  main { padding: 0; }
  body { background: #fff; color: #000; font-size: 11pt; }
  h2 { break-before: page; border: 0; margin-top: 0; }
  figure, .callout, .table-wrap, pre { break-inside: avoid; }
  figure img { box-shadow: none; }
  a { color: inherit; }
}
</style>
</head>
<body>
<header class="top">
  <button id="menu-btn" type="button" aria-label="Contents" aria-controls="toc">☰</button>
  <div class="logo" aria-hidden="true">H</div>
  <span class="name">Premium HRM</span><span class="ver">User Guide · Version 1.0</span>
  <span class="spacer"></span>
  <button id="theme-btn" type="button" aria-label="Toggle dark mode">◐<span class="lbl"> Theme</span></button>
  <button id="print-btn" type="button" onclick="window.print()">Print / PDF</button>
</header>
<div class="layout">
  <nav class="toc" id="toc" aria-label="Contents">{{TOC}}</nav>
  <main><article>
{{BODY}}
  </article></main>
</div>
<script>
(function () {
  var root = document.documentElement, KEY = 'hrm-guide-theme';
  try { var saved = localStorage.getItem(KEY); if (saved) root.setAttribute('data-theme', saved); } catch (e) {}
  document.getElementById('theme-btn').addEventListener('click', function () {
    var dark = root.getAttribute('data-theme') ? root.getAttribute('data-theme') === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
    var next = dark ? 'light' : 'dark';
    root.setAttribute('data-theme', next);
    try { localStorage.setItem(KEY, next); } catch (e) {}
  });
  document.getElementById('menu-btn').addEventListener('click', function () { document.body.classList.toggle('menu-open'); });
  var links = Array.prototype.slice.call(document.querySelectorAll('nav.toc a'));
  links.forEach(function (a) { a.addEventListener('click', function () { document.body.classList.remove('menu-open'); }); });
  var targets = links.map(function (a) { return document.getElementById(a.getAttribute('href').slice(1)); });
  function update() {
    var current = 0;
    for (var i = 0; i < targets.length; i++) if (targets[i] && targets[i].getBoundingClientRect().top < 120) current = i;
    links.forEach(function (a) { a.classList.remove('active'); });
    document.querySelectorAll('nav.toc li.open').forEach(function (li) { li.classList.remove('open'); });
    var a = links[current]; if (!a) return;
    a.classList.add('active');
    var li = a.closest('nav.toc > ul > li'); if (li) li.classList.add('open');
  }
  document.addEventListener('scroll', update, { passive: true }); update();
})();
</script>
</body>
</html>
"""

if __name__ == "__main__":
    main()
