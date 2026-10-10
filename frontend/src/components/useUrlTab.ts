import { useCallback } from 'react';
import { useSearchParams } from 'react-router';

/** Keeps the active tab in `?tab=` so tabs survive reloads, can be linked to and work with Back. */
export function useUrlTab(defaultTab: string, allowed?: string[]): [string, (tab: string | null) => void] {
  const [params, setParams] = useSearchParams();
  const raw = params.get('tab');
  const tab = raw && (!allowed || allowed.includes(raw)) ? raw : defaultTab;
  const setTab = useCallback(
    (next: string | null) => {
      setParams(
        (prev) => {
          const p = new URLSearchParams(prev);
          if (!next || next === defaultTab) p.delete('tab');
          else p.set('tab', next);
          return p;
        },
        { replace: true },
      );
    },
    [setParams, defaultTab],
  );
  return [tab, setTab];
}
