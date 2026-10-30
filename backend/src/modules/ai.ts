import crypto from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import type { Prisma } from '@prisma/client';
import { AppError } from '../lib/errors';
import { prisma } from '../lib/prisma';
import { currentUser, requireAuth } from '../middleware/auth';
import { aiLimiter } from '../middleware/rateLimit';
import { imageUpload, sniffImageMime } from '../middleware/upload';
import { parseBody } from '../middleware/validate';
import { generateRecipes, scanIngredients, suggestSubstitutions, type GeneratedRecipe } from '../services/gemini';
import { computeMatch } from '../services/matching';
import { normalizeIngredients } from '../services/normalizer';
import { toRecipeData, saveRecipe } from '../services/recipeStore';
import { timerFromText, type RecipeData } from '../services/recipeTypes';
import { pantrySnapshot } from './pantry';

export const aiRouter = Router();
aiRouter.use(requireAuth, aiLimiter);

const MIN_CONFIDENCE = 0.4;

aiRouter.post('/scan-ingredients', imageUpload, async (req, res) => {
  const userId = currentUser(req);
  if (!req.file) throw new AppError(400, 'IMAGE_REQUIRED', 'Pilih gambar terlebih dahulu.');
  const mimeType = sniffImageMime(req.file.buffer);

  const result = await scanIngredients(req.file.buffer, mimeType);
  const ingredients = result.ingredients.filter((i) => i.confidence >= MIN_CONFIDENCE);

  // Nothing here enters the pantry: the app must send the reviewed list to /api/pantry/bulk.
  await prisma.scanHistory.create({
    data: {
      userId,
      ingredients: ingredients as unknown as Prisma.InputJsonValue,
      imageMime: mimeType,
      imageBytes: req.file.size,
    },
  });

  const message =
    result.image_quality === 'blurry'
      ? 'Gambar kurang jelas. Coba foto ulang dengan pencahayaan lebih baik, atau tambahkan bahan manual.'
      : ingredients.length === 0
        ? 'Tidak ada bahan makanan yang dikenali. Coba foto lain atau tambahkan bahan manual.'
        : null;
  res.json({ imageQuality: result.image_quality, ingredients, message });
});

const generateBody = z.object({
  ingredients: z.array(z.string().trim().min(1).max(120)).max(50).optional(),
  maxMinutes: z.number().int().positive().max(600).optional(),
  servings: z.number().int().positive().max(50).optional(),
  category: z.string().trim().max(80).optional(),
  notes: z.string().trim().max(500).optional(),
  count: z.number().int().min(1).max(4).default(1),
});

const stringList = (value: unknown): string[] =>
  Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : [];

/** A last check on the model's output: allergy entries must not appear in any ingredient. */
function containsAllergen(recipe: GeneratedRecipe, allergies: string[]): boolean {
  const names = recipe.ingredients.flatMap((i) => [i.name.toLowerCase(), i.name_en.toLowerCase()]);
  return allergies.some((a) => names.some((n) => n.includes(a.toLowerCase())));
}

function toRecipe(generated: GeneratedRecipe): RecipeData {
  const id = crypto.randomUUID();
  const hasNutrition = Object.values(generated.nutrition).some((v) => v !== null);
  return {
    key: `ai-${id}`,
    source: 'AI',
    externalId: id,
    title: generated.recipe_name,
    description: generated.description,
    imageUrl: null,
    sourceUrl: null,
    cookingMinutes: generated.cooking_time,
    servings: generated.servings,
    difficulty: generated.difficulty,
    cuisine: null,
    category: null,
    nutrition: hasNutrition ? generated.nutrition : null,
    nutritionIsEstimate: hasNutrition,
    ingredients: generated.ingredients.map((i) => ({
      name: i.name_en.toLowerCase(),
      displayName: i.name,
      original: null,
      quantity: i.quantity,
      unit: i.unit,
      required: i.required,
    })),
    instructions: generated.instructions.map((text, index) => ({
      step: index + 1,
      text,
      timerSeconds: timerFromText(text),
    })),
  };
}

aiRouter.post('/generate-recipe', async (req, res) => {
  const userId = currentUser(req);
  const { count, ...body } = parseBody(generateBody, req);
  const [{ pantry, expiring }, profile] = await Promise.all([
    pantrySnapshot(userId),
    prisma.userProfile.findUnique({ where: { userId } }),
  ]);
  const ingredients = body.ingredients?.length ? body.ingredients : pantry;
  if (ingredients.length === 0) {
    throw new AppError(400, 'NO_INGREDIENTS', 'Tambahkan minimal satu bahan terlebih dahulu.');
  }

  const allergies = stringList(profile?.allergies);
  const generated = await generateRecipes(
    {
      ...body,
      ingredients,
      preferences: stringList(profile?.dietaryPreferences),
      allergies,
      dislikedIngredients: stringList(profile?.dislikedIngredients),
    },
    count,
  );

  const safe = generated.filter((r) => !containsAllergen(r, allergies));
  if (safe.length === 0) {
    throw new AppError(422, 'AI_ALLERGEN_REJECTED', 'Resep AI mengandung alergen kamu. Coba lagi.');
  }

  const owned = [...pantry, ...(await normalizeIngredients(ingredients))];
  const recipes = [];
  for (const recipe of safe) {
    const stored = toRecipeData(await saveRecipe(toRecipe(recipe), userId));
    recipes.push({ ...stored, isAiGenerated: true, match: computeMatch(stored.ingredients, owned, expiring) });
  }
  res.status(201).json({ recipes });
});

aiRouter.post('/substitute-ingredients', async (req, res) => {
  const userId = currentUser(req);
  const body = parseBody(
    z.object({
      recipeTitle: z.string().trim().max(255).optional(),
      missing: z.array(z.string().trim().min(1).max(160)).min(1).max(20),
      available: z.array(z.string().trim().min(1).max(160)).max(100).optional(),
    }),
    req,
  );
  const [{ pantry }, profile] = await Promise.all([
    pantrySnapshot(userId),
    prisma.userProfile.findUnique({ where: { userId } }),
  ]);
  const result = await suggestSubstitutions({
    recipeTitle: body.recipeTitle,
    missing: body.missing,
    available: body.available ?? pantry,
    allergies: stringList(profile?.allergies),
  });
  res.json(result);
});
