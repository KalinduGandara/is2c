import { mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import type { Locator, Page } from '@playwright/test';

/** Screenshots land in the user guide's image folder. */
export const OUT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../docs/user-guide/images');
mkdirSync(OUT, { recursive: true });

export type Place = 'below' | 'above' | 'right' | 'left' | 'inside';

/** One highlighted element: a red outline, an optional numbered badge and an optional caption. */
export interface Mark {
  el: Locator;
  n?: number;
  text?: string;
  place?: Place;
  pad?: number;
  /** Badge corner: top-left (default) or top-right, when the left corner would cover a neighbour. */
  badgeAt?: 'tl' | 'tr';
}

interface Box {
  x: number;
  y: number;
  width: number;
  height: number;
  n?: number;
  text?: string;
  place?: Place;
  pad?: number;
  badgeAt?: 'tl' | 'tr';
}

/** The whole labelled field (label, description, input and pills) around an input. */
export const field = (input: Locator) => input.locator('xpath=ancestor::div[contains(@class,"mantine-InputWrapper-root")][1]');
/** The bordered card (Mantine Paper) that contains an element. */
export const card = (el: Locator) => el.locator('xpath=ancestor::div[contains(@class,"mantine-Paper-root")][1]');

/** Draws callouts over the page (absolute overlay, removed again after the screenshot). */
export async function annotate(page: Page, marks: Mark[]) {
  // Scroll once (to the first mark, only if needed) and measure everything afterwards, so boxes stay aligned.
  await marks[0]?.el.first().scrollIntoViewIfNeeded().catch(() => undefined);
  await page.waitForTimeout(150);
  const vp = page.viewportSize()!;
  const boxes: Box[] = [];
  for (const m of marks) {
    const b = await m.el.first().boundingBox();
    if (!b) throw new Error(`annotate: element not visible (${m.text ?? m.n ?? ''})`);
    if (b.y + b.height < 0 || b.y > vp.height) {
      console.warn(`annotate: mark ${m.n ?? ''} "${m.text ?? ''}" is outside the viewport — skipped`);
      continue;
    }
    boxes.push({ ...b, n: m.n, text: m.text, place: m.place, pad: m.pad, badgeAt: m.badgeAt });
  }
  await page.evaluate((items) => {
    document.getElementById('guide-overlay')?.remove();
    const root = document.createElement('div');
    root.id = 'guide-overlay';
    root.style.cssText = 'position:absolute;left:0;top:0;width:0;height:0;z-index:2147483647;pointer-events:none;';
    document.body.appendChild(root);
    const RED = '#e8364d';
    const minX = window.scrollX + 4;
    const maxX = window.scrollX + window.innerWidth - 4;
    const minY = window.scrollY + 4;
    const maxY = window.scrollY + window.innerHeight - 4;
    for (const b of items) {
      const pad = b.pad ?? 5;
      const x = Math.max(b.x + window.scrollX - pad, minX - 2);
      const y = Math.max(b.y + window.scrollY - pad, minY - 2);
      const w = Math.min(b.width + pad * 2, maxX - x + 2);
      const h = Math.min(b.height + pad * 2, maxY - y + 2);
      const frame = document.createElement('div');
      frame.style.cssText = `position:absolute;left:${x}px;top:${y}px;width:${w}px;height:${h}px;border:3px solid ${RED};border-radius:9px;box-shadow:0 0 0 4px rgba(232,54,77,.18);box-sizing:border-box;`;
      root.appendChild(frame);
      if (b.n !== undefined) {
        // On the top-left corner, outside the box (usually empty space); inside the box when there is no room.
        const size = 28;
        let bx = b.badgeAt === 'tr' ? x + w - 4 : x - size + 4;
        let by = y - size + 4;
        if (bx < minX) bx = x + 4;
        if (bx + size > maxX) bx = x + w - size - 4;
        if (by < minY) by = y + 4;
        const badge = document.createElement('div');
        badge.textContent = String(b.n);
        badge.style.cssText = `position:absolute;left:${bx}px;top:${by}px;width:${size}px;height:${size}px;border-radius:50%;background:${RED};color:#fff;font:700 15px/${size}px Inter,system-ui,sans-serif;text-align:center;border:2px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,.3);box-sizing:content-box;`;
        root.appendChild(badge);
      }
      if (b.text) {
        const label = document.createElement('div');
        label.textContent = b.text;
        label.style.cssText = 'position:absolute;left:0;top:0;width:max-content;max-width:280px;background:#1f2330;color:#fff;font:600 13px/1.35 Inter,system-ui,sans-serif;padding:7px 11px;border-radius:7px;box-shadow:0 4px 14px rgba(0,0,0,.28);white-space:normal;';
        root.appendChild(label);
        const lw = label.offsetWidth;
        const lh = label.offsetHeight;
        let lx = x;
        let ly = y + h + 8;
        if (b.place === 'above') ly = y - lh - 8;
        if (b.place === 'right') { lx = x + w + 12; ly = y + h / 2 - lh / 2; }
        if (b.place === 'left') { lx = x - lw - 12; ly = y + h / 2 - lh / 2; }
        if (b.place === 'inside') { lx = x + 8; ly = y + 8; }
        // keep the caption on screen
        lx = Math.min(Math.max(lx, minX), maxX - lw);
        ly = Math.min(Math.max(ly, minY), maxY - lh);
        label.style.left = `${lx}px`;
        label.style.top = `${ly}px`;
      }
    }
  }, boxes);
}

export interface ShotOptions {
  /** Capture only this element (plus a margin) instead of the viewport. */
  clip?: Locator;
  margin?: number;
  fullPage?: boolean;
  /** Dense forms: draw boxes and numbers only; the guide text explains each number. */
  numbersOnly?: boolean;
}

/** Takes `<name>.png`, optionally annotated. */
export async function shot(page: Page, name: string, marks: Mark[] = [], opts: ShotOptions = {}) {
  await page.mouse.move(0, 0);
  await page.waitForTimeout(450); // let transitions and skeletons settle
  if (marks.length) await annotate(page, opts.numbersOnly ? marks.map((m) => ({ ...m, text: undefined })) : marks);
  const file = path.join(OUT, `${name}.png`);
  if (opts.clip) {
    const b = await opts.clip.first().boundingBox();
    if (!b) throw new Error(`shot ${name}: clip element not visible`);
    const m = opts.margin ?? 16;
    const vp = page.viewportSize()!;
    const x = Math.max(0, b.x - m);
    const y = Math.max(0, b.y - m);
    await page.screenshot({
      path: file,
      clip: { x, y, width: Math.min(vp.width - x, b.width + m * 2), height: Math.min(vp.height - y, b.height + m * 2) },
    });
  } else {
    await page.screenshot({ path: file, fullPage: opts.fullPage });
  }
  await page.evaluate(() => document.getElementById('guide-overlay')?.remove());
}
