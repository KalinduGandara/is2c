import en from './en.json';
import raw from './en.json?raw';

/** NFR-010: the English bundle must be well-formed — duplicate keys silently shadow each other in JSON. */
describe('NFR-010 i18n bundle', () => {
  it('has no duplicate keys within an object', () => {
    const duplicates: string[] = [];
    const stack: Set<string>[] = [new Set()];
    for (const match of raw.matchAll(/"([^"]+)"\s*:\s*(\{)?|(\})/g)) {
      if (match[3]) {
        stack.pop();
        continue;
      }
      const keys = stack[stack.length - 1];
      if (keys) {
        if (keys.has(match[1])) duplicates.push(match[1]);
        keys.add(match[1]);
      }
      if (match[2]) stack.push(new Set());
    }
    expect(duplicates).toEqual([]);
  });

  it('defines every literal key used with t() in the sources', () => {
    const sources = import.meta.glob(['../**/*.{ts,tsx}', '!../**/*.test.{ts,tsx}'], { query: '?raw', import: 'default', eager: true }) as Record<string, string>;
    const resolve = (key: string) => key.split('.').reduce<unknown>((node, part) => (node && typeof node === 'object' ? (node as Record<string, unknown>)[part] : undefined), en);
    const missing = new Set<string>();
    let used = 0;
    for (const [file, text] of Object.entries(sources)) {
      for (const m of text.matchAll(/\bt\(\s*['"]([\w.-]+)['"]/g)) {
        used++;
        if (typeof resolve(m[1]) !== 'string') missing.add(`${m[1]} (${file})`);
      }
    }
    expect(used).toBeGreaterThan(10);
    expect([...missing]).toEqual([]);
  });

  it('defines the product title', () => {
    expect(en.app.title).toBe('Premium HRM');
  });
});
