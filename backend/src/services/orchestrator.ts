import { cache } from '../lib/cache';
import { logger } from '../lib/logger';
import { computeMatch, dedupeKey, type MatchResult } from './matching';
import type { RecipeData } from './recipeTypes';
import * as spoonacular from './spoonacular';
import * as themealdb from './themealdb';

const RESULT_TTL_MS = 30 * 60_000;
const SPOONACULAR_LIMIT = 15;
const MEALDB_LIMIT = 12;
/** TheMealDB is only worth calling when Spoonacular left fewer good hits than this. */
const ENOUGH_GOOD_RESULTS = 6;
const GOOD_MATCH_PERCENT = 50;
const EXPIRING_BONUS = 10;

export interface RecipeSummary extends MatchResult {
  key: string;
  source: RecipeData['source'];
  title: string;
  imageUrl: string | null;
  cookingMinutes: number | null;
  servings: number | null;
  difficulty: string | null;
  nutrition: RecipeData['nutrition'];
  isAiGenerated: boolean;
}

export interface SourceStatus {
  spoonacular: 'ok' | 'failed' | 'skipped';
  themealdb: 'ok' | 'failed' | 'skipped';
}

export interface FindResult {
  recipes: RecipeSummary[];
  sources: SourceStatus;
  /** True when no recipe API produced anything, so the app should offer AI generation. */
  suggestAi: boolean;
}

export function toSummary(recipe: RecipeData, pantry: string[], expiring: string[]): RecipeSummary {
  return {
    key: recipe.key,
    source: recipe.source,
    title: recipe.title,
    imageUrl: recipe.imageUrl,
    cookingMinutes: recipe.cookingMinutes,
    servings: recipe.servings,
    difficulty: recipe.difficulty,
    nutrition: recipe.nutrition,
    isAiGenerated: recipe.source === 'AI',
    ...computeMatch(recipe.ingredients, pantry, expiring),
  };
}

export function mergeAndRank(recipes: RecipeData[], pantry: string[], expiring: string[]): RecipeSummary[] {
  const best = new Map<string, RecipeSummary>();
  for (const recipe of recipes) {
    const summary = toSummary(recipe, pantry, expiring);
    const key = dedupeKey(recipe.title);
    const existing = best.get(key);
    if (!existing || summary.matchPercent > existing.matchPercent) best.set(key, summary);
  }
  const score = (r: RecipeSummary) => r.matchPercent + r.usesExpiring.length * EXPIRING_BONUS;
  return [...best.values()].sort((a, b) => score(b) - score(a) || a.missing.length - b.missing.length);
}

async function fetchFromSources(
  fromSpoonacular: () => Promise<RecipeData[]>,
  fromMealDb: () => Promise<RecipeData[]>,
  pantry: string[],
): Promise<{ recipes: RecipeData[]; sources: SourceStatus }> {
  const sources: SourceStatus = { spoonacular: 'skipped', themealdb: 'skipped' };
  const recipes: RecipeData[] = [];

  if (spoonacular.isSpoonacularAvailable()) {
    try {
      recipes.push(...(await fromSpoonacular()));
      sources.spoonacular = 'ok';
    } catch (err) {
      sources.spoonacular = 'failed';
      logger.warn({ err: (err as Error).message }, 'Spoonacular failed; falling back to TheMealDB');
    }
  }

  const goodHits = recipes.filter(
    (r) => computeMatch(r.ingredients, pantry).matchPercent >= GOOD_MATCH_PERCENT,
  ).length;
  if (goodHits < ENOUGH_GOOD_RESULTS) {
    try {
      recipes.push(...(await fromMealDb()));
      sources.themealdb = 'ok';
    } catch (err) {
      sources.themealdb = 'failed';
      logger.warn({ err: (err as Error).message }, 'TheMealDB failed');
    }
  }
  return { recipes, sources };
}

export interface FindOptions {
  /** English names to search with. Expiring items should come first. */
  ingredients: string[];
  /** The user's full English pantry, for scoring. */
  pantry: string[];
  expiring: string[];
}

export async function findByIngredients({ ingredients, pantry, expiring }: FindOptions): Promise<FindResult> {
  const cacheKey = `find:${[...ingredients].sort().join('|')}`;
  const cached = cache.get<{ recipes: RecipeData[]; sources: SourceStatus }>(cacheKey);
  const { recipes, sources } =
    cached ??
    (await fetchFromSources(
      () => spoonacular.searchByIngredients(ingredients, SPOONACULAR_LIMIT),
      () => themealdb.searchByIngredients(ingredients, MEALDB_LIMIT),
      pantry,
    ));

  if (!cached && recipes.length > 0) cache.set(cacheKey, { recipes, sources }, RESULT_TTL_MS);
  return { recipes: mergeAndRank(recipes, pantry, expiring), sources, suggestAi: recipes.length === 0 };
}

export async function searchRecipes(query: string, pantry: string[], expiring: string[]): Promise<FindResult> {
  const cacheKey = `search:${query}`;
  const cached = cache.get<{ recipes: RecipeData[]; sources: SourceStatus }>(cacheKey);
  const { recipes, sources } =
    cached ??
    (await fetchFromSources(
      () => spoonacular.searchByText(query, SPOONACULAR_LIMIT),
      () => themealdb.searchByText(query),
      pantry,
    ));

  if (!cached && recipes.length > 0) cache.set(cacheKey, { recipes, sources }, RESULT_TTL_MS);
  return { recipes: mergeAndRank(recipes, pantry, expiring), sources, suggestAi: recipes.length === 0 };
}
