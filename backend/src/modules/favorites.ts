import { Router } from 'express';
import { z } from 'zod';
import { notFound } from '../lib/errors';
import { prisma } from '../lib/prisma';
import { currentUser, requireAuth } from '../middleware/auth';
import { parseBody, parseQuery } from '../middleware/validate';
import { toSummary } from '../services/orchestrator';
import { loadRecipe, toRecipeData } from '../services/recipeStore';
import { pantrySnapshot } from './pantry';

export const favoritesRouter = Router();
favoritesRouter.use(requireAuth);

async function ownedCollection(id: string, userId: string) {
  const collection = await prisma.recipeCollection.findFirst({ where: { id, userId } });
  if (!collection) throw notFound('Koleksi');
  return collection;
}

favoritesRouter.get('/', async (req, res) => {
  const userId = currentUser(req);
  const { q, collectionId } = parseQuery(
    z.object({ q: z.string().trim().max(120).optional(), collectionId: z.uuid().optional() }),
    req,
  );
  const [{ pantry, expiring }, favorites] = await Promise.all([
    pantrySnapshot(userId),
    prisma.favoriteRecipe.findMany({
      where: {
        userId,
        ...(collectionId ? { collectionId } : {}),
        ...(q ? { recipe: { title: { contains: q } } } : {}),
      },
      orderBy: { createdAt: 'desc' },
      include: {
        collection: true,
        recipe: {
          include: { ingredients: { orderBy: { position: 'asc' } }, instructions: { orderBy: { step: 'asc' } } },
        },
      },
    }),
  ]);
  res.json(
    favorites.map((f) => ({
      id: f.id,
      collectionId: f.collectionId,
      collectionName: f.collection?.name ?? null,
      recipe: toSummary(toRecipeData(f.recipe), pantry, expiring),
    })),
  );
});

favoritesRouter.post('/', async (req, res) => {
  const userId = currentUser(req);
  const { recipeKey, collectionId } = parseBody(
    z.object({ recipeKey: z.string().min(1).max(80), collectionId: z.uuid().nullable().optional() }),
    req,
  );
  if (collectionId) await ownedCollection(collectionId, userId);
  const recipe = await loadRecipe(recipeKey, userId);
  const favorite = await prisma.favoriteRecipe.upsert({
    where: { userId_recipeId: { userId, recipeId: recipe.id } },
    create: { userId, recipeId: recipe.id, collectionId: collectionId ?? null },
    update: collectionId !== undefined ? { collectionId } : {},
  });
  res.status(201).json({ id: favorite.id, collectionId: favorite.collectionId });
});

favoritesRouter.put('/:id', async (req, res) => {
  const userId = currentUser(req);
  const { collectionId } = parseBody(z.object({ collectionId: z.uuid().nullable() }), req);
  if (collectionId) await ownedCollection(collectionId, userId);
  const result = await prisma.favoriteRecipe.updateMany({ where: { id: req.params.id, userId }, data: { collectionId } });
  if (result.count === 0) throw notFound('Favorit');
  res.json({ id: req.params.id, collectionId });
});

favoritesRouter.delete('/:id', async (req, res) => {
  const result = await prisma.favoriteRecipe.deleteMany({ where: { id: req.params.id, userId: currentUser(req) } });
  if (result.count === 0) throw notFound('Favorit');
  res.status(204).end();
});

export const collectionsRouter = Router();
collectionsRouter.use(requireAuth);

const collectionBody = z.object({ name: z.string().trim().min(1).max(120) });

collectionsRouter.get('/', async (req, res) => {
  const collections = await prisma.recipeCollection.findMany({
    where: { userId: currentUser(req) },
    orderBy: { name: 'asc' },
    include: { _count: { select: { favorites: true } } },
  });
  res.json(collections.map((c) => ({ id: c.id, name: c.name, recipeCount: c._count.favorites })));
});

collectionsRouter.post('/', async (req, res) => {
  const { name } = parseBody(collectionBody, req);
  const collection = await prisma.recipeCollection.create({ data: { name, userId: currentUser(req) } });
  res.status(201).json({ id: collection.id, name: collection.name, recipeCount: 0 });
});

collectionsRouter.put('/:id', async (req, res) => {
  const collection = await ownedCollection(req.params.id, currentUser(req));
  const { name } = parseBody(collectionBody, req);
  await prisma.recipeCollection.update({ where: { id: collection.id }, data: { name } });
  res.json({ id: collection.id, name });
});

collectionsRouter.delete('/:id', async (req, res) => {
  const collection = await ownedCollection(req.params.id, currentUser(req));
  await prisma.recipeCollection.delete({ where: { id: collection.id } });
  res.status(204).end();
});
