import { Router } from 'express';
import { z } from 'zod';
import { addDays, formatDate, parseDate, today, weekStartOf } from '../lib/dates';
import { notFound } from '../lib/errors';
import { prisma } from '../lib/prisma';
import { currentUser, requireAuth } from '../middleware/auth';
import { parseBody, parseQuery } from '../middleware/validate';
import { loadRecipe } from '../services/recipeStore';

export const mealPlansRouter = Router();
mealPlansRouter.use(requireAuth);

const itemInclude = { recipe: { select: { key: true, title: true, imageUrl: true, cookingMinutes: true } } } as const;

mealPlansRouter.get('/', async (req, res) => {
  const userId = currentUser(req);
  const { weekStart } = parseQuery(z.object({ weekStart: z.iso.date().optional() }), req);
  const start = weekStartOf(weekStart ? parseDate(weekStart) : today());
  const items = await prisma.mealPlanItem.findMany({
    where: { mealPlan: { userId, weekStart: start } },
    include: itemInclude,
    orderBy: [{ date: 'asc' }, { mealType: 'asc' }],
  });
  res.json({
    weekStart: formatDate(start),
    weekEnd: formatDate(addDays(start, 6)),
    items: items.map((i) => ({
      id: i.id,
      date: formatDate(i.date),
      mealType: i.mealType,
      servings: i.servings,
      recipe: i.recipe,
    })),
  });
});

const itemBody = z.object({
  recipeKey: z.string().min(1).max(80),
  date: z.iso.date(),
  mealType: z.enum(['BREAKFAST', 'LUNCH', 'DINNER']),
  servings: z.number().int().positive().max(50).nullable().optional(),
});

/** Assigns a recipe to a slot; a filled slot is replaced. */
mealPlansRouter.post('/', async (req, res) => {
  const userId = currentUser(req);
  const body = parseBody(itemBody, req);
  const date = parseDate(body.date);
  const weekStart = weekStartOf(date);
  const recipe = await loadRecipe(body.recipeKey, userId);

  const plan = await prisma.mealPlan.upsert({
    where: { userId_weekStart: { userId, weekStart } },
    create: { userId, weekStart },
    update: {},
  });
  const item = await prisma.mealPlanItem.upsert({
    where: { mealPlanId_date_mealType: { mealPlanId: plan.id, date, mealType: body.mealType } },
    create: { mealPlanId: plan.id, recipeId: recipe.id, date, mealType: body.mealType, servings: body.servings ?? null },
    update: { recipeId: recipe.id, servings: body.servings ?? null },
    include: itemInclude,
  });
  res.status(201).json({
    id: item.id,
    date: formatDate(item.date),
    mealType: item.mealType,
    servings: item.servings,
    recipe: item.recipe,
  });
});

mealPlansRouter.put('/:id', async (req, res) => {
  const userId = currentUser(req);
  const body = parseBody(z.object({ recipeKey: z.string().min(1).max(80), servings: z.number().int().positive().max(50).nullable().optional() }), req);
  const item = await prisma.mealPlanItem.findFirst({ where: { id: req.params.id, mealPlan: { userId } } });
  if (!item) throw notFound('Rencana makan');
  const recipe = await loadRecipe(body.recipeKey, userId);
  const updated = await prisma.mealPlanItem.update({
    where: { id: item.id },
    data: { recipeId: recipe.id, servings: body.servings ?? item.servings },
    include: itemInclude,
  });
  res.json({
    id: updated.id,
    date: formatDate(updated.date),
    mealType: updated.mealType,
    servings: updated.servings,
    recipe: updated.recipe,
  });
});

mealPlansRouter.delete('/:id', async (req, res) => {
  const result = await prisma.mealPlanItem.deleteMany({
    where: { id: req.params.id, mealPlan: { userId: currentUser(req) } },
  });
  if (result.count === 0) throw notFound('Rencana makan');
  res.status(204).end();
});
