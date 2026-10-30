import { Router } from 'express';
import { z } from 'zod';
import { AppError } from '../lib/errors';
import { prisma } from '../lib/prisma';
import { currentUser, requireAuth } from '../middleware/auth';
import { parseQuery } from '../middleware/validate';
import { computeMatch } from '../services/matching';
import { normalizeIngredients, translateQuery } from '../services/normalizer';
import { findByIngredients, searchRecipes, toSummary, type FindResult, type RecipeSummary } from '../services/orchestrator';
import { loadRecipe, toRecipeData } from '../services/recipeStore';
import { pantrySnapshot } from './pantry';

export const recipesRouter = Router();
recipesRouter.use(requireAuth);

const MAX_SEARCH_INGREDIENTS = 8;

const filters = z.object({
  minMatch: z.coerce.number().int().min(0).max(100).default(0),
  /** "available": only recipes needing nothing extra. "few": at most maxMissing extra items. "any": no limit. */
  mode: z.enum(['available', 'few', 'any']).default('any'),
  maxMissing: z.coerce.number().int().min(0).max(20).default(3),
});

function applyFilters(result: FindResult, f: z.output<typeof filters>): FindResult {
  const recipes = result.recipes.filter(
    (r: RecipeSummary) =>
      r.matchPercent >= f.minMatch &&
      (f.mode === 'any' ||
        (f.mode === 'available' ? r.missing.length === 0 : r.missing.length <= f.maxMissing)),
  );
  return { ...result, recipes };
}

recipesRouter.get('/search', async (req, res) => {
  const userId = currentUser(req);
  const { q, ...rest } = parseQuery(filters.extend({ q: z.string().trim().min(1).max(120) }), req);
  const { pantry, expiring } = await pantrySnapshot(userId);
  res.json(applyFilters(await searchRecipes(translateQuery(q), pantry, expiring), rest));
});

async function byIngredients(userId: string, names: string[] | undefined, f: z.output<typeof filters>) {
  const { pantry, expiring } = await pantrySnapshot(userId);
  const selected = names?.length ? await normalizeIngredients(names) : pantry;
  if (selected.length === 0) return { recipes: [], sources: { spoonacular: 'skipped', themealdb: 'skipped' }, suggestAi: false };

  // Expiring items go first so they survive the per-request ingredient cap.
  const ordered = [...selected.filter((n) => expiring.includes(n)), ...selected.filter((n) => !expiring.includes(n))];
  const result = await findByIngredients({
    ingredients: ordered.slice(0, MAX_SEARCH_INGREDIENTS),
    pantry: [...new Set([...pantry, ...selected])],
    expiring,
  });
  return applyFilters(result, f);
}

recipesRouter.get('/by-ingredients', async (req, res) => {
  const { ingredients, ...rest } = parseQuery(
    filters.extend({ ingredients: z.string().trim().max(1000).optional() }),
    req,
  );
  const names = ingredients?.split(',').map((n) => n.trim()).filter(Boolean);
  res.json(await byIngredients(currentUser(req), names, rest));
});

recipesRouter.get('/recommendations', async (req, res) => {
  res.json(await byIngredients(currentUser(req), undefined, parseQuery(filters, req)));
});

recipesRouter.get('/recently-viewed', async (req, res) => {
  const userId = currentUser(req);
  const [{ pantry, expiring }, views] = await Promise.all([
    pantrySnapshot(userId),
    prisma.recipeView.findMany({
      where: { userId },
      orderBy: { viewedAt: 'desc' },
      take: 10,
      include: {
        recipe: {
          include: { ingredients: { orderBy: { position: 'asc' } }, instructions: { orderBy: { step: 'asc' } } },
        },
      },
    }),
  ]);
  res.json(views.map((v) => toSummary(toRecipeData(v.recipe), pantry, expiring)));
});

recipesRouter.get('/:key', async (req, res) => {
  const userId = currentUser(req);
  const key = req.params.key;
  if (!/^(spoonacular|mealdb|ai)-[0-9a-f-]+$/i.test(key)) {
    throw new AppError(400, 'INVALID_RECIPE_ID', 'ID resep tidak valid.');
  }

  const row = await loadRecipe(key, userId);
  const [{ pantry, expiring }, favorite] = await Promise.all([
    pantrySnapshot(userId),
    prisma.favoriteRecipe.findUnique({ where: { userId_recipeId: { userId, recipeId: row.id } } }),
    prisma.recipeView.upsert({
      where: { userId_recipeId: { userId, recipeId: row.id } },
      create: { userId, recipeId: row.id },
      update: { viewedAt: new Date() },
    }),
  ]);

  const recipe = toRecipeData(row);
  res.json({
    ...recipe,
    isAiGenerated: recipe.source === 'AI',
    favoriteId: favorite?.id ?? null,
    match: computeMatch(recipe.ingredients, pantry, expiring),
    ingredients: recipe.ingredients.map((i) => ({
      ...i,
      available: computeMatch([i], pantry).available.length > 0,
    })),
  });
});
