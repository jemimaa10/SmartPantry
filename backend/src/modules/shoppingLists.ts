import type { Prisma } from '@prisma/client';
import { Router } from 'express';
import { z } from 'zod';
import { notFound } from '../lib/errors';
import { prisma } from '../lib/prisma';
import { currentUser, requireAuth } from '../middleware/auth';
import { parseBody } from '../middleware/validate';
import { lookupCatalog } from '../services/normalizer';

export const shoppingListsRouter = Router();
shoppingListsRouter.use(requireAuth);

const include: Prisma.ShoppingListInclude = {
  items: { orderBy: [{ isPurchased: 'asc' }, { category: 'asc' }, { name: 'asc' }] },
};

const itemFields = z.object({
  name: z.string().trim().min(1).max(160),
  quantity: z.number().positive().max(1_000_000).nullable().optional(),
  unit: z.string().trim().max(40).nullable().optional(),
  category: z.string().trim().max(80).nullable().optional(),
  recipeKey: z.string().max(80).nullable().optional(),
});

const OTHER_CATEGORY = 'Lainnya';

/** Looks names up in the catalog so items from recipes group by aisle. */
async function categoriesFor(names: string[]): Promise<Map<string, string>> {
  const english = new Map(names.flatMap((n) => {
    const known = lookupCatalog(n);
    return known ? [[n, known] as const] : [];
  }));
  const rows = await prisma.ingredient.findMany({
    where: { scope: 'global', nameEn: { in: [...english.values()] } },
    include: { category: true },
  });
  const byName = new Map(rows.map((r) => [r.nameEn, r.category.nameId]));
  return new Map([...english].flatMap(([name, en]) => {
    const category = byName.get(en);
    return category ? [[name, category] as const] : [];
  }));
}

async function ownedList(id: string, userId: string) {
  const list = await prisma.shoppingList.findFirst({ where: { id, userId } });
  if (!list) throw notFound('Daftar belanja');
  return list;
}

async function ownedItem(listId: string, itemId: string, userId: string) {
  const list = await ownedList(listId, userId);
  const item = await prisma.shoppingListItem.findFirst({ where: { id: itemId, listId: list.id } });
  if (!item) throw notFound('Item');
  return item;
}

shoppingListsRouter.get('/', async (req, res) => {
  res.json(
    await prisma.shoppingList.findMany({
      where: { userId: currentUser(req) },
      include,
      orderBy: { createdAt: 'asc' },
    }),
  );
});

shoppingListsRouter.post('/', async (req, res) => {
  const { name, items } = parseBody(
    z.object({ name: z.string().trim().min(1).max(120), items: z.array(itemFields).max(100).default([]) }),
    req,
  );
  const list = await prisma.shoppingList.create({
    data: { name, userId: currentUser(req), items: { create: items } },
    include,
  });
  res.status(201).json(list);
});

shoppingListsRouter.put('/:id', async (req, res) => {
  const list = await ownedList(req.params.id, currentUser(req));
  const { name } = parseBody(z.object({ name: z.string().trim().min(1).max(120) }), req);
  res.json(await prisma.shoppingList.update({ where: { id: list.id }, data: { name }, include }));
});

shoppingListsRouter.delete('/:id', async (req, res) => {
  const list = await ownedList(req.params.id, currentUser(req));
  await prisma.shoppingList.delete({ where: { id: list.id } });
  res.status(204).end();
});

/** Adds items, skipping names already waiting to be bought on this list. */
shoppingListsRouter.post('/:id/items', async (req, res) => {
  const list = await ownedList(req.params.id, currentUser(req));
  const { items } = parseBody(z.object({ items: z.array(itemFields).min(1).max(100) }), req);
  const pending = await prisma.shoppingListItem.findMany({
    where: { listId: list.id, isPurchased: false },
    select: { name: true },
  });
  const seen = new Set(pending.map((i) => i.name.toLowerCase()));
  const fresh = items.filter((i) => {
    const key = i.name.toLowerCase();
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
  const categories = await categoriesFor(fresh.map((i) => i.name));
  await prisma.shoppingListItem.createMany({
    data: fresh.map((i) => ({
      ...i,
      category: i.category ?? categories.get(i.name) ?? OTHER_CATEGORY,
      listId: list.id,
    })),
  });
  res.status(201).json(await prisma.shoppingList.findUniqueOrThrow({ where: { id: list.id }, include }));
});

shoppingListsRouter.put('/:id/items/:itemId', async (req, res) => {
  const item = await ownedItem(req.params.id, req.params.itemId, currentUser(req));
  const body = parseBody(itemFields.partial().extend({ isPurchased: z.boolean().optional() }), req);
  res.json(await prisma.shoppingListItem.update({ where: { id: item.id }, data: body }));
});

shoppingListsRouter.delete('/:id/items/:itemId', async (req, res) => {
  const item = await ownedItem(req.params.id, req.params.itemId, currentUser(req));
  await prisma.shoppingListItem.delete({ where: { id: item.id } });
  res.status(204).end();
});
