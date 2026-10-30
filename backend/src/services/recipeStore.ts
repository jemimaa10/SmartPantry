import { Prisma } from '@prisma/client';
import { notFound } from '../lib/errors';
import { logger } from '../lib/logger';
import { prisma } from '../lib/prisma';
import type { NutritionData, RecipeData } from './recipeTypes';
import * as spoonacular from './spoonacular';
import * as themealdb from './themealdb';

const SPOONACULAR_MAX_AGE_MS = 3_600_000;
const MEALDB_MAX_AGE_MS = 7 * 86_400_000;

const withChildren = {
  ingredients: { orderBy: { position: 'asc' } },
  instructions: { orderBy: { step: 'asc' } },
} as const;

type StoredRecipe = Prisma.RecipeGetPayload<{ include: typeof withChildren }>;

export function toRecipeData(row: StoredRecipe): RecipeData {
  return {
    key: row.key,
    source: row.source,
    externalId: row.externalId,
    title: row.title,
    description: row.description,
    imageUrl: row.imageUrl,
    sourceUrl: row.sourceUrl,
    cookingMinutes: row.cookingMinutes,
    servings: row.servings,
    difficulty: row.difficulty,
    cuisine: row.cuisine,
    category: row.category,
    nutrition: row.nutrition as NutritionData | null,
    nutritionIsEstimate: row.nutritionIsEstimate,
    ingredients: row.ingredients.map((i) => ({
      name: i.name,
      displayName: i.displayName,
      original: i.original,
      quantity: i.quantity,
      unit: i.unit,
      required: i.isRequired,
    })),
    instructions: row.instructions.map((i) => ({ step: i.step, text: i.text, timerSeconds: i.timerSeconds })),
  };
}

const clip = (value: string | null, max: number) => (value && value.length > max ? value.slice(0, max) : value);

export async function saveRecipe(recipe: RecipeData, ownerId?: string): Promise<StoredRecipe> {
  const fields = {
    source: recipe.source,
    externalId: recipe.externalId,
    title: recipe.title.slice(0, 255),
    description: recipe.description,
    imageUrl: clip(recipe.imageUrl, 500),
    sourceUrl: clip(recipe.sourceUrl, 500),
    cookingMinutes: recipe.cookingMinutes,
    servings: recipe.servings,
    difficulty: recipe.difficulty,
    cuisine: clip(recipe.cuisine, 80),
    category: clip(recipe.category, 80),
    nutrition: recipe.nutrition ? (recipe.nutrition as unknown as Prisma.InputJsonValue) : Prisma.JsonNull,
    nutritionIsEstimate: recipe.nutritionIsEstimate,
    fetchedAt: new Date(),
  };

  return prisma.$transaction(async (tx) => {
    const row = await tx.recipe.upsert({
      where: { key: recipe.key },
      create: { key: recipe.key, ownerId, ...fields },
      update: fields,
    });
    await tx.recipeIngredient.deleteMany({ where: { recipeId: row.id } });
    await tx.recipeInstruction.deleteMany({ where: { recipeId: row.id } });
    await tx.recipeIngredient.createMany({
      data: recipe.ingredients.map((i, position) => ({
        recipeId: row.id,
        position,
        name: i.name.slice(0, 160),
        displayName: clip(i.displayName, 160),
        original: clip(i.original, 300),
        quantity: i.quantity,
        unit: clip(i.unit, 40),
        isRequired: i.required,
      })),
    });
    await tx.recipeInstruction.createMany({
      data: recipe.instructions.map((i) => ({ recipeId: row.id, ...i })),
    });
    return tx.recipe.findUniqueOrThrow({ where: { id: row.id }, include: withChildren });
  });
}

async function fetchExternal(key: string): Promise<RecipeData | null> {
  const separator = key.indexOf('-');
  const source = key.slice(0, separator);
  const id = key.slice(separator + 1);
  if (!/^\d+$/.test(id)) return null;
  if (source === 'spoonacular') return spoonacular.getRecipe(id);
  if (source === 'mealdb') return themealdb.getRecipe(id);
  return null;
}

/** Loads a recipe, refreshing stale external copies; a stored copy survives upstream outages. */
export async function loadRecipe(key: string, userId: string): Promise<StoredRecipe> {
  const stored = await prisma.recipe.findUnique({ where: { key }, include: withChildren });

  if (stored?.source === 'AI') {
    if (stored.ownerId !== userId) throw notFound('Resep');
    return stored;
  }

  const maxAge = stored?.source === 'SPOONACULAR' ? SPOONACULAR_MAX_AGE_MS : MEALDB_MAX_AGE_MS;
  if (stored && Date.now() - stored.fetchedAt.getTime() < maxAge) return stored;

  try {
    const fresh = await fetchExternal(key);
    if (fresh) return await saveRecipe(fresh);
  } catch (err) {
    if (!stored) throw err;
    logger.warn({ key, err: (err as Error).message }, 'Serving stored recipe after refresh failed');
  }
  if (stored) return stored;
  throw notFound('Resep');
}
