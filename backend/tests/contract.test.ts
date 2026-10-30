import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import request from 'supertest';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { createApp } from '../src/app';
import { env } from '../src/config/env';
import { cache } from '../src/lib/cache';
import { prisma } from '../src/lib/prisma';

/**
 * Records real responses as fixtures. The Android ContractTest decodes them, so
 * a field change on either side fails a test rather than crashing the app.
 */
const FIXTURES = path.resolve(__dirname, '../../app/src/test/resources/contract');
const app = createApp();
const realFetch = globalThis.fetch;

const json = (body: unknown) =>
  new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
const gemini = (payload: unknown) => json({ candidates: [{ content: { parts: [{ text: JSON.stringify(payload) }] } }] });

const spoonRecipe = {
  id: 1001,
  title: 'Telur Tomat',
  image: 'https://img.example/1.jpg',
  readyInMinutes: 15,
  servings: 2,
  extendedIngredients: [
    { nameClean: 'egg', original: '2 eggs', amount: 2, unit: '' },
    { nameClean: 'rice', original: '1 cup rice', amount: 1, unit: 'cup' },
    { nameClean: 'parsley', original: 'parsley, for garnish', amount: 1, unit: 'tbsp' },
  ],
  analyzedInstructions: [{ steps: [{ step: 'Cook for 5 minutes.' }] }],
  nutrition: { nutrients: [{ name: 'Calories', amount: 210 }] },
};

const meal = {
  idMeal: '52772',
  strMeal: 'Teriyaki Chicken',
  strCategory: 'Chicken',
  strArea: 'Japanese',
  strInstructions: 'Bake for 20 minutes.',
  strMealThumb: 'https://img.example/m.jpg',
  strIngredient1: 'Chicken',
  strMeasure1: '500g',
};

function upstream(url: string): Response {
  if (url.includes('generativelanguage')) {
    if (lastPrompt.includes('photos')) {
      return gemini({
        image_quality: 'good',
        ingredients: [{ name: 'Egg', name_id: 'Telur', confidence: 0.98, quantity: 2, unit: 'pieces' }],
      });
    }
    if (lastPrompt.includes('substitutions')) {
      return gemini({
        substitutions: [
          {
            missing_ingredient: 'Mentega',
            substitute: null,
            how_to_substitute: '',
            taste_texture_impact: '',
            suitability: 'not_recommended',
            safety_note: 'Catatan',
          },
        ],
      });
    }
    return gemini({
      recipes: [
        {
          recipe_name: 'Telur Ceplok',
          description: 'Sederhana.',
          ingredients: [{ name: 'Telur', name_en: 'egg', quantity: 1, unit: 'butir', available: true, required: true }],
          instructions: ['Goreng selama 3 menit.'],
          cooking_time: 5,
          servings: 1,
          difficulty: 'Easy',
          nutrition: { calories: null, protein: null, carbohydrates: null, fat: null },
        },
      ],
    });
  }
  if (url.includes('/complexSearch')) return json({ results: [spoonRecipe] });
  if (url.includes('/information')) return json(spoonRecipe);
  if (url.includes('/filter.php')) return json({ meals: [{ idMeal: '52772' }] });
  return json({ meals: [meal] });
}

let lastPrompt = '';

function save(name: string, body: unknown) {
  writeFileSync(path.join(FIXTURES, `${name}.json`), `${JSON.stringify(body, null, 2)}\n`);
}

beforeAll(() => {
  mkdirSync(FIXTURES, { recursive: true });
  env.SPOONACULAR_API_KEY = 'contract-key';
  env.GEMINI_API_KEY = 'contract-key';
  globalThis.fetch = vi.fn((input: string | URL | Request, init?: RequestInit) => {
    lastPrompt = typeof init?.body === 'string' ? init.body : '';
    return Promise.resolve(upstream(String(input)));
  }) as typeof fetch;
});

afterAll(async () => {
  globalThis.fetch = realFetch;
  await prisma.$disconnect();
});

describe('Android contract fixtures', () => {
  it('records every response the app decodes', async () => {
    cache.clear();
    const email = `contract-${crypto.randomUUID()}@test.dev`;
    const session = await request(app).post('/api/auth/register').send({ name: 'Kontrak', email, password: 'password123' }).expect(201);
    save('session', session.body);
    const headers = { Authorization: `Bearer ${session.body.accessToken}` };
    const get = async (url: string) => (await request(app).get(url).set(headers).expect(200)).body;

    save('me', await get('/api/users/me'));
    save('preferences', (await request(app).put('/api/users/preferences').set(headers).send({ allergies: ['udang'] }).expect(200)).body);
    save('categories', await get('/api/ingredients/categories'));
    save('ingredients', await get('/api/ingredients?q=bawang'));

    const item = await request(app)
      .post('/api/pantry')
      .set(headers)
      .send({ name: 'bayam', quantity: 6, unit: 'ikat', expiresAt: '2020-01-01', notes: 'kulkas' })
      .expect(201);
    save('pantry_item', item.body);
    save('pantry_bulk', (await request(app).post('/api/pantry/bulk').set(headers).send({ items: [{ name: 'telur' }, { name: 'tomat' }] }).expect(201)).body);
    save('pantry', await get('/api/pantry'));
    save('expiring', await get('/api/pantry/expiring'));

    save('find_result', await get('/api/recipes/recommendations'));
    save('search_result', await get('/api/recipes/search?q=ayam'));
    save('recipe_detail', await get('/api/recipes/spoonacular-1001'));
    save('recently_viewed', await get('/api/recipes/recently-viewed'));

    const jpeg = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(32)]);
    save(
      'scan',
      (await request(app).post('/api/ai/scan-ingredients').set(headers).attach('image', jpeg, { filename: 'a.jpg', contentType: 'image/jpeg' }).expect(200)).body,
    );
    save('history', await get('/api/history'));
    save('generate', (await request(app).post('/api/ai/generate-recipe').set(headers).send({ ingredients: ['telur'] }).expect(201)).body);
    save('substitute', (await request(app).post('/api/ai/substitute-ingredients').set(headers).send({ missing: ['Mentega'] }).expect(200)).body);

    const collection = await request(app).post('/api/collections').set(headers).send({ name: 'Malam' }).expect(201);
    save('collection', collection.body);
    const favorite = await request(app).post('/api/favorites').set(headers).send({ recipeKey: 'mealdb-52772', collectionId: collection.body.id }).expect(201);
    save('favorite_created', favorite.body);
    save('collections', await get('/api/collections'));
    save('favorites', await get('/api/favorites'));

    const lists = await get('/api/shopping-lists');
    const added = await request(app).post(`/api/shopping-lists/${lists[0].id}/items`).set(headers).send({ items: [{ name: 'Beras', quantity: 1, unit: 'kg' }] }).expect(201);
    save('shopping_list', added.body);
    save('shopping_lists', await get('/api/shopping-lists'));
    save(
      'shopping_item',
      (await request(app).put(`/api/shopping-lists/${lists[0].id}/items/${added.body.items[0].id}`).set(headers).send({ isPurchased: true, quantity: null }).expect(200)).body,
    );

    save(
      'meal_item',
      (await request(app).post('/api/meal-plans').set(headers).send({ recipeKey: 'mealdb-52772', date: '2030-07-03', mealType: 'DINNER' }).expect(201)).body,
    );
    save('meal_week', await get('/api/meal-plans?weekStart=2030-07-01'));

    const error = await request(app).post('/api/auth/login').send({ email, password: 'wrong-password' }).expect(401);
    save('error', error.body);
    expect(error.body.error.code).toBe('UNAUTHORIZED');
  });
});
