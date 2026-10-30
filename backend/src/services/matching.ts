import type { RecipeIngredientData } from './recipeTypes';

/**
 * Modifiers that turn an ingredient into a different product. Pantry "milk"
 * must not satisfy "coconut milk", nor "tomato" satisfy "tomato sauce".
 */
const DISTINCT_PRODUCT_WORDS = new Set([
  'almond', 'bouillon', 'broth', 'butter', 'cake', 'cheese', 'chip', 'coconut', 'cream', 'cube', 'extract',
  'flake', 'flour', 'juice', 'ketchup', 'milk', 'noodle', 'oat', 'oil', 'paste', 'peanut', 'powder', 'puree',
  'rice', 'sauce', 'sesame', 'sour', 'soy', 'stock', 'sweet', 'syrup', 'vinegar', 'wine',
]);

const DESCRIPTOR_WORDS = new Set([
  'boneless', 'skinless', 'chopped', 'diced', 'dried', 'fresh', 'frozen', 'ground', 'large', 'medium', 'minced',
  'organic', 'raw', 'ripe', 'sliced', 'small', 'whole',
]);

const OPTIONAL_PATTERN = /\b(optional|garnish|to serve|for serving|if desired|if you like|to taste)\b/i;
const ALWAYS_OPTIONAL = new Set(['water', 'ice']);

function singular(word: string): string {
  if (word.endsWith('oes')) return word.slice(0, -2);
  if (word.endsWith('ies')) return `${word.slice(0, -3)}y`;
  if (word.endsWith('ss') || word.length <= 3) return word;
  return word.endsWith('s') ? word.slice(0, -1) : word;
}

export function tokens(name: string): string[] {
  return name
    .toLowerCase()
    .replace(/[^a-z\s-]/g, ' ')
    .split(/[\s-]+/)
    .filter((w) => w.length > 1 && !DESCRIPTOR_WORDS.has(w))
    .map(singular);
}

export function ingredientsMatch(a: string, b: string): boolean {
  const left = tokens(a);
  const right = tokens(b);
  if (left.length === 0 || right.length === 0) return false;
  const [shorter, longer] = left.length <= right.length ? [left, right] : [right, left];
  if (!shorter.every((t) => longer.includes(t))) return false;
  return longer.filter((t) => !shorter.includes(t)).every((t) => !DISTINCT_PRODUCT_WORDS.has(t));
}

export const isOptionalIngredient = (name: string, original?: string | null) =>
  ALWAYS_OPTIONAL.has(tokens(name).join(' ')) || OPTIONAL_PATTERN.test(original ?? '');

export interface MatchResult {
  matchPercent: number;
  available: string[];
  missing: string[];
  optionalMissing: string[];
  usesExpiring: string[];
}

export function computeMatch(
  ingredients: RecipeIngredientData[],
  pantry: string[],
  expiring: string[] = [],
): MatchResult {
  const available: string[] = [];
  const missing: string[] = [];
  const optionalMissing: string[] = [];
  const usesExpiring = new Set<string>();
  let requiredTotal = 0;
  let requiredAvailable = 0;

  for (const ingredient of ingredients) {
    const has = pantry.some((p) => ingredientsMatch(p, ingredient.name));
    const label = ingredient.displayName ?? ingredient.name;
    if (ingredient.required) requiredTotal += 1;
    if (has) {
      available.push(label);
      if (ingredient.required) requiredAvailable += 1;
      for (const e of expiring) if (ingredientsMatch(e, ingredient.name)) usesExpiring.add(e);
    } else if (ingredient.required) {
      missing.push(label);
    } else {
      optionalMissing.push(label);
    }
  }

  return {
    matchPercent: requiredTotal === 0 ? 0 : Math.round((requiredAvailable / requiredTotal) * 100),
    available,
    missing,
    optionalMissing,
    usesExpiring: [...usesExpiring],
  };
}

const TITLE_NOISE = new Set(['a', 'an', 'and', 'easy', 'best', 'homemade', 'simple', 'the', 'with', 'quick']);

/** Key for spotting the same dish across sources. */
export const dedupeKey = (title: string) =>
  title
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .split(/\s+/)
    .filter((w) => w && !TITLE_NOISE.has(w))
    .map(singular)
    .sort()
    .join(' ');
