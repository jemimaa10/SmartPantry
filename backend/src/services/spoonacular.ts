import { z } from 'zod';
import { env } from '../config/env';
import { fetchWithRetry, UpstreamError } from '../lib/http';
import { logger } from '../lib/logger';
import { isOptionalIngredient } from './matching';
import { timerFromText, type NutritionData, type RecipeData } from './recipeTypes';

const BASE_URL = 'https://api.spoonacular.com';

let quotaBlockedUntil = 0;

const nextUtcMidnight = () => {
  const now = new Date();
  return Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1);
};

export const isSpoonacularAvailable = () => Boolean(env.SPOONACULAR_API_KEY) && Date.now() >= quotaBlockedUntil;

export const resetSpoonacularQuota = () => {
  quotaBlockedUntil = 0;
};

async function get(path: string, params: Record<string, string | number | boolean>): Promise<unknown> {
  if (!isSpoonacularAvailable()) throw new UpstreamError('spoonacular', null, 'spoonacular unavailable');
  const query = new URLSearchParams(Object.entries(params).map(([k, v]) => [k, String(v)]));
  try {
    const response = await fetchWithRetry(`${BASE_URL}${path}?${query}`, {
      service: 'spoonacular',
      timeoutMs: 8_000,
      retries: 2,
      headers: { 'x-api-key': env.SPOONACULAR_API_KEY ?? '' },
    });
    const left = Number(response.headers.get('x-api-quota-left'));
    if (Number.isFinite(left) && response.headers.has('x-api-quota-left') && left <= 0) {
      quotaBlockedUntil = nextUtcMidnight();
    }
    return await response.json();
  } catch (err) {
    // 402 means the daily point quota is spent; retrying before reset only wastes calls.
    if (err instanceof UpstreamError && err.status === 402) {
      quotaBlockedUntil = nextUtcMidnight();
      logger.warn('Spoonacular daily quota exhausted; pausing until UTC midnight');
    }
    throw err;
  }
}

const ingredientSchema = z.object({
  name: z.string().optional(),
  nameClean: z.string().nullish(),
  original: z.string().nullish(),
  amount: z.number().nullish(),
  unit: z.string().nullish(),
});

const nutrientSchema = z.object({ name: z.string(), amount: z.number() });

const informationSchema = z.object({
  id: z.number(),
  title: z.string(),
  image: z.string().nullish(),
  summary: z.string().nullish(),
  sourceUrl: z.string().nullish(),
  readyInMinutes: z.number().nullish(),
  servings: z.number().nullish(),
  cuisines: z.array(z.string()).nullish(),
  dishTypes: z.array(z.string()).nullish(),
  extendedIngredients: z.array(ingredientSchema).nullish(),
  analyzedInstructions: z
    .array(z.object({ steps: z.array(z.object({ step: z.string() })) }))
    .nullish(),
  nutrition: z.object({ nutrients: z.array(nutrientSchema) }).nullish(),
});

function mapNutrition(nutrients: z.output<typeof nutrientSchema>[] | undefined): NutritionData | null {
  if (!nutrients?.length) return null;
  const amount = (name: string) => nutrients.find((n) => n.name === name)?.amount ?? null;
  return {
    calories: amount('Calories'),
    protein: amount('Protein'),
    carbohydrates: amount('Carbohydrates'),
    fat: amount('Fat'),
  };
}

export function mapInformation(raw: z.output<typeof informationSchema>): RecipeData {
  const ingredients = (raw.extendedIngredients ?? []).flatMap((i) => {
    const name = (i.nameClean ?? i.name ?? '').toLowerCase().trim();
    if (!name) return [];
    return [
      {
        name,
        displayName: null,
        original: i.original ?? null,
        quantity: i.amount ?? null,
        unit: i.unit || null,
        required: !isOptionalIngredient(name, i.original),
      },
    ];
  });
  const instructions = (raw.analyzedInstructions ?? [])
    .flatMap((group) => group.steps)
    .map((s, index) => ({ step: index + 1, text: s.step, timerSeconds: timerFromText(s.step) }));

  return {
    key: `spoonacular-${raw.id}`,
    source: 'SPOONACULAR',
    externalId: String(raw.id),
    title: raw.title,
    description: raw.summary?.replace(/<[^>]*>/g, '').slice(0, 800) ?? null,
    imageUrl: raw.image ?? null,
    sourceUrl: raw.sourceUrl ?? null,
    cookingMinutes: raw.readyInMinutes ?? null,
    servings: raw.servings ?? null,
    difficulty: null,
    cuisine: raw.cuisines?.[0] ?? null,
    category: raw.dishTypes?.[0] ?? null,
    nutrition: mapNutrition(raw.nutrition?.nutrients),
    nutritionIsEstimate: false,
    ingredients,
    instructions,
  };
}

const parse = <S extends z.ZodType>(schema: S, value: unknown): z.output<S> => {
  const result = schema.safeParse(value);
  if (!result.success) throw new UpstreamError('spoonacular', null, 'spoonacular response failed validation');
  return result.data;
};

export async function getRecipe(id: string): Promise<RecipeData> {
  const json = await get(`/recipes/${encodeURIComponent(id)}/information`, { includeNutrition: true });
  return mapInformation(parse(informationSchema, json));
}

/**
 * complexSearch with fillIngredients returns the same fields as the information
 * endpoint at far lower quota cost than findByIngredients plus a lookup per hit.
 */
export async function searchByIngredients(names: string[], limit: number): Promise<RecipeData[]> {
  const json = await get('/recipes/complexSearch', {
    includeIngredients: names.join(','),
    sort: 'min-missing-ingredients',
    fillIngredients: true,
    addRecipeInformation: true,
    addRecipeInstructions: true,
    addRecipeNutrition: true,
    number: limit,
  });
  return parse(z.object({ results: z.array(informationSchema) }), json).results.map(mapInformation);
}

export async function searchByText(query: string, limit: number): Promise<RecipeData[]> {
  const json = await get('/recipes/complexSearch', {
    query,
    fillIngredients: true,
    addRecipeInformation: true,
    addRecipeInstructions: true,
    addRecipeNutrition: true,
    number: limit,
  });
  return parse(z.object({ results: z.array(informationSchema) }), json).results.map(mapInformation);
}
