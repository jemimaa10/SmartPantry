import { z } from 'zod';
import { env } from '../config/env';
import { cache } from '../lib/cache';
import { fetchWithRetry, UpstreamError } from '../lib/http';
import { isOptionalIngredient } from './matching';
import { timerFromText, type RecipeData } from './recipeTypes';

const DAY_MS = 86_400_000;
const LOOKUP_CONCURRENCY = 4;

const baseUrl = () => `https://www.themealdb.com/api/json/v1/${encodeURIComponent(env.THEMEALDB_API_KEY)}`;

async function get(path: string): Promise<unknown> {
  const response = await fetchWithRetry(`${baseUrl()}${path}`, { service: 'themealdb', timeoutMs: 8_000, retries: 2 });
  return response.json();
}

const mealSchema = z.looseObject({
  idMeal: z.string(),
  strMeal: z.string(),
  strCategory: z.string().nullish(),
  strArea: z.string().nullish(),
  strInstructions: z.string().nullish(),
  strMealThumb: z.string().nullish(),
  strSource: z.string().nullish(),
});

const listSchema = z.object({ meals: z.array(mealSchema).nullable() });
const summarySchema = z.object({ meals: z.array(z.object({ idMeal: z.string() })).nullable().or(z.string()) });

function parseMeals(json: unknown) {
  const result = listSchema.safeParse(json);
  if (!result.success) throw new UpstreamError('themealdb', null, 'themealdb response failed validation');
  return result.data.meals ?? [];
}

/** Splits "3/4 cup" or "1 1/2 tbs" into quantity and unit. */
export function parseMeasure(measure: string): { quantity: number | null; unit: string | null } {
  const text = measure.trim();
  if (!text) return { quantity: null, unit: null };
  const match = /^(\d+\s+\d+\/\d+|\d+\/\d+|\d+(?:\.\d+)?)\s*(.*)$/.exec(text);
  if (!match?.[1]) return { quantity: null, unit: text };
  const value = match[1].split(/\s+/).reduce((sum, part) => {
    const [top, bottom] = part.split('/').map(Number);
    return sum + (bottom ? (top ?? 0) / bottom : (top ?? 0));
  }, 0);
  return { quantity: Math.round(value * 1000) / 1000, unit: match[2]?.trim() || null };
}

export function mapMeal(meal: z.output<typeof mealSchema>): RecipeData {
  const ingredients: RecipeData['ingredients'] = [];
  for (let i = 1; i <= 20; i++) {
    const name = String(meal[`strIngredient${i}`] ?? '').trim().toLowerCase();
    if (!name) continue;
    const measure = String(meal[`strMeasure${i}`] ?? '').trim();
    const original = `${measure} ${name}`.trim();
    ingredients.push({
      name,
      displayName: null,
      original,
      ...parseMeasure(measure),
      required: !isOptionalIngredient(name, original),
    });
  }

  const steps = (meal.strInstructions ?? '')
    .split(/\r?\n+/)
    .map((line) => line.replace(/^\s*(step\s*)?\d+[.):]?\s*$/i, '').trim())
    .filter(Boolean);

  return {
    key: `mealdb-${meal.idMeal}`,
    source: 'MEALDB',
    externalId: meal.idMeal,
    title: meal.strMeal,
    description: null,
    imageUrl: meal.strMealThumb ?? null,
    sourceUrl: meal.strSource ?? null,
    cookingMinutes: null,
    servings: null,
    difficulty: null,
    cuisine: meal.strArea ?? null,
    category: meal.strCategory ?? null,
    nutrition: null,
    nutritionIsEstimate: false,
    ingredients,
    instructions: steps.map((text, index) => ({ step: index + 1, text, timerSeconds: timerFromText(text) })),
  };
}

export function getRecipe(id: string): Promise<RecipeData | null> {
  return cache.getOrSet(`mealdb:lookup:${id}`, DAY_MS, async () => {
    const [meal] = parseMeals(await get(`/lookup.php?i=${encodeURIComponent(id)}`));
    return meal ? mapMeal(meal) : null;
  });
}

export async function searchByText(query: string): Promise<RecipeData[]> {
  const meals = await cache.getOrSet(`mealdb:search:${query}`, DAY_MS, async () =>
    parseMeals(await get(`/search.php?s=${encodeURIComponent(query)}`)),
  );
  return meals.map(mapMeal);
}

async function idsForIngredient(name: string): Promise<string[]> {
  return cache.getOrSet(`mealdb:filter:${name}`, DAY_MS, async () => {
    const json = await get(`/filter.php?i=${encodeURIComponent(name.replace(/\s+/g, '_'))}`);
    const result = summarySchema.safeParse(json);
    if (!result.success) throw new UpstreamError('themealdb', null, 'themealdb response failed validation');
    return Array.isArray(result.data.meals) ? result.data.meals.map((m) => m.idMeal) : [];
  });
}

/**
 * filter.php takes one ingredient and returns ids only, so meals appearing for
 * the most pantry ingredients are looked up in full, in small batches.
 */
export async function searchByIngredients(names: string[], limit: number): Promise<RecipeData[]> {
  const lists = await Promise.allSettled(names.slice(0, 4).map(idsForIngredient));
  if (lists.every((l) => l.status === 'rejected')) {
    throw new UpstreamError('themealdb', null, 'themealdb filter failed');
  }

  const hits = new Map<string, number>();
  for (const list of lists) {
    if (list.status === 'fulfilled') for (const id of list.value) hits.set(id, (hits.get(id) ?? 0) + 1);
  }
  const ids = [...hits.entries()].sort((a, b) => b[1] - a[1]).slice(0, limit).map(([id]) => id);

  const recipes: RecipeData[] = [];
  for (let i = 0; i < ids.length; i += LOOKUP_CONCURRENCY) {
    const batch = await Promise.allSettled(ids.slice(i, i + LOOKUP_CONCURRENCY).map(getRecipe));
    for (const result of batch) if (result.status === 'fulfilled' && result.value) recipes.push(result.value);
  }
  return recipes;
}
