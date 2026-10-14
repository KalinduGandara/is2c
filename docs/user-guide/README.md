# Premium HRM — User Guide

| File | Purpose |
|---|---|
| `USER_GUIDE.md` | The guide (single source). Renders on GitHub/GitLab, including the Mermaid diagrams and callouts. |
| `index.html` | The same guide as a styled web page (contents sidebar, numbered figures, dark mode, **Print / PDF** button). Generated — do not edit. |
| `images/` | Annotated screenshots. Generated — do not edit by hand. |
| `build_html.py` | Builds `index.html` from `USER_GUIDE.md` and fails on broken links or missing images. |

**Sharing with customers:** send the whole folder (or a zip of it); open `index.html` in a browser. To make a PDF,
open `index.html` and use **Print / PDF** (A4, each chapter starts on a new page).

## Updating

1. Edit `USER_GUIDE.md`.
2. If screens changed, regenerate the screenshots (about 3 minutes; uses a fresh database on port 8083 and Vite on
   5176, and shows the app at 10:30 on the current day):
   ```bash
   cd frontend && npx playwright test -c playwright.guide.config.ts
   ```
   The tour lives in `frontend/guide/user-guide.spec.ts`; callouts are drawn by `frontend/guide/annotate.ts`.
   Numbered callouts in the text must match the badge numbers in the screenshots.
3. Rebuild the HTML:
   ```bash
   python3 docs/user-guide/build_html.py     # needs: pip install markdown
   ```
