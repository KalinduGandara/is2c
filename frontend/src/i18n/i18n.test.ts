import en from './en.json';
import raw from './en.json?raw';

/** NFR-010: the English bundle must be well-formed — duplicate keys silently shadow each other in JSON. */
describe('i18n bundle', () => {
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

  it('defines the product title', () => {
    expect(en.app.title).toBe('Premium HRM');
  });
});
