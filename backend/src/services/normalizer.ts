import { CATALOG, DISH_WORDS } from '../data/catalog';
import { cache } from '../lib/cache';
import { logger } from '../lib/logger';
import { isGeminiConfigured, normalizeNames } from './gemini';

const DAY_MS = 86_400_000;

const aliasMap = new Map<string, string>();
for (const [nameEn, nameId, , , aliases] of CATALOG) {
  aliasMap.set(nameEn, nameEn);
  aliasMap.set(nameId.toLowerCase(), nameEn);
  for (const alias of aliases ?? []) aliasMap.set(alias.toLowerCase(), nameEn);
}

const clean = (name: string) => name.trim().toLowerCase().replace(/\s+/g, ' ');

export const lookupCatalog = (name: string) => aliasMap.get(clean(name));

/**
 * Maps ingredient names to the English names recipe APIs expect: the local
 * catalog first, then Gemini for unknown names. Falls back to the input text
 * so a Gemini outage never blocks recipe search.
 */
export async function normalizeIngredients(names: string[]): Promise<string[]> {
  const unknown = [...new Set(names.map(clean))].filter(
    (n) => !aliasMap.has(n) && cache.get(`normalize:${n}`) === undefined,
  );

  if (unknown.length > 0 && isGeminiConfigured()) {
    try {
      const translated = await normalizeNames(unknown);
      for (const name of unknown) {
        const english = translated.get(name);
        if (english) cache.set(`normalize:${name}`, english, DAY_MS * 30);
      }
    } catch (err) {
      logger.warn({ err: (err as Error).message }, 'Ingredient normalisation fell back to raw names');
    }
  }

  const resolved = names.map(clean).map((n) => aliasMap.get(n) ?? cache.get<string>(`normalize:${n}`) ?? n);
  return [...new Set(resolved)];
}

/** Translates a free-text recipe query word by word, longest catalog phrase first. */
export function translateQuery(query: string): string {
  const words = clean(query).split(' ');
  const output: string[] = [];
  for (let i = 0; i < words.length; ) {
    let consumed = 0;
    for (let length = Math.min(3, words.length - i); length >= 1 && !consumed; length--) {
      const phrase = words.slice(i, i + length).join(' ');
      const translated = aliasMap.get(phrase) ?? (length === 1 ? DISH_WORDS[phrase] : undefined);
      if (translated) {
        output.push(translated);
        consumed = length;
      }
    }
    if (consumed === 0) {
      output.push(words[i] ?? '');
      consumed = 1;
    }
    i += consumed;
  }
  return output.join(' ').trim();
}
