import { Router } from 'express';
import { z } from 'zod';
import { ingredientSearchText } from '../data/catalog';
import { AppError, notFound } from '../lib/errors';
import { prisma } from '../lib/prisma';
import { currentUser, requireAuth } from '../middleware/auth';
import { parseBody, parseQuery } from '../middleware/validate';

export const ingredientsRouter = Router();
ingredientsRouter.use(requireAuth);

const withCategory = { category: true } as const;

ingredientsRouter.get('/categories', async (_req, res) => {
  res.json(await prisma.ingredientCategory.findMany({ orderBy: { sortOrder: 'asc' } }));
});

ingredientsRouter.get('/', async (req, res) => {
  const userId = currentUser(req);
  const { q, category, limit } = parseQuery(
    z.object({
      q: z.string().trim().max(80).optional(),
      category: z.string().trim().max(60).optional(),
      limit: z.coerce.number().int().min(1).max(100).default(20),
    }),
    req,
  );

  const ingredients = await prisma.ingredient.findMany({
    where: {
      OR: [{ createdById: null }, { createdById: userId }],
      ...(category ? { category: { slug: category } } : {}),
      ...(q ? { searchText: { contains: q.toLowerCase() } } : {}),
    },
    include: withCategory,
    orderBy: { nameId: 'asc' },
    take: limit,
  });
  res.json(ingredients);
});

const ingredientBody = z.object({
  nameEn: z.string().trim().toLowerCase().min(1).max(120),
  nameId: z.string().trim().min(1).max(120),
  categorySlug: z.string().trim().max(60).default('other'),
  defaultUnit: z.string().trim().min(1).max(30).default('pcs'),
});

async function categoryId(slug: string): Promise<number> {
  const category = await prisma.ingredientCategory.findUnique({ where: { slug } });
  if (!category) throw new AppError(400, 'UNKNOWN_CATEGORY', 'Kategori tidak dikenal.');
  return category.id;
}

ingredientsRouter.post('/', async (req, res) => {
  const userId = currentUser(req);
  const { categorySlug, ...body } = parseBody(ingredientBody, req);
  const created = await prisma.ingredient.create({
    data: {
      ...body,
      aliases: [],
      searchText: ingredientSearchText(body.nameEn, body.nameId),
      categoryId: await categoryId(categorySlug),
      createdById: userId,
      scope: userId,
    },
    include: withCategory,
  });
  res.status(201).json(created);
});

async function ownedIngredient(id: string, userId: string) {
  const ingredient = await prisma.ingredient.findFirst({ where: { id, createdById: userId } });
  if (!ingredient) throw notFound('Bahan');
  return ingredient;
}

ingredientsRouter.put('/:id', async (req, res) => {
  const ingredient = await ownedIngredient(req.params.id, currentUser(req));
  const { categorySlug, ...body } = parseBody(ingredientBody.partial(), req);
  const updated = await prisma.ingredient.update({
    where: { id: ingredient.id },
    data: {
      ...body,
      searchText: ingredientSearchText(body.nameEn ?? ingredient.nameEn, body.nameId ?? ingredient.nameId),
      ...(categorySlug ? { categoryId: await categoryId(categorySlug) } : {}),
    },
    include: withCategory,
  });
  res.json(updated);
});

ingredientsRouter.delete('/:id', async (req, res) => {
  const ingredient = await ownedIngredient(req.params.id, currentUser(req));
  const inUse = await prisma.pantryItem.count({ where: { ingredientId: ingredient.id } });
  if (inUse > 0) throw new AppError(409, 'INGREDIENT_IN_USE', 'Bahan masih ada di pantry.');
  await prisma.ingredient.delete({ where: { id: ingredient.id } });
  res.status(204).end();
});
