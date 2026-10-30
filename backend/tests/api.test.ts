import request from 'supertest';
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { createApp } from '../src/app';
import { env } from '../src/config/env';
import { cache } from '../src/lib/cache';
import { prisma } from '../src/lib/prisma';
import { resetSpoonacularQuota } from '../src/services/spoonacular';

const app = createApp();
const realFetch = globalThis.fetch;

type Mock = (url: string, init?: RequestInit) => Response | Promise<Response>;
const json = (body: unknown, status = 200, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } });

let upstream: Mock;
const calls: string[] = [];

function geminiReply(payload: unknown) {
  return json({ candidates: [{ content: { parts: [{ text: JSON.stringify(payload) }] } }] });
}

const spoonRecipe = {
  id: 716429,
  title: 'Tomato Egg Scramble',
  image: 'https://img.example/spoon.jpg',
  readyInMinutes: 15,
  servings: 2,
  summary: '<b>Nice</b> dish',
  extendedIngredients: [
    { nameClean: 'egg', original: '2 eggs', amount: 2, unit: '' },
    { nameClean: 'tomato', original: '1 tomato', amount: 1, unit: '' },
    { nameClean: 'parsley', original: 'parsley, for garnish', amount: 1, unit: 'tbsp' },
  ],
  analyzedInstructions: [{ steps: [{ step: 'Beat eggs.' }, { step: 'Cook 5 minutes.' }] }],
  nutrition: {
    nutrients: [
      { name: 'Calories', amount: 210 },
      { name: 'Protein', amount: 13 },
      { name: 'Carbohydrates', amount: 4 },
      { name: 'Fat', amount: 15 },
    ],
  },
};

const meal = {
  idMeal: '52772',
  strMeal: 'Teriyaki Chicken Casserole',
  strCategory: 'Chicken',
  strArea: 'Japanese',
  strInstructions: 'Preheat oven.\r\nBake for 20 minutes.',
  strMealThumb: 'https://img.example/meal.jpg',
  strIngredient1: 'Chicken',
  strMeasure1: '500g',
  strIngredient2: 'Soy Sauce',
  strMeasure2: '3/4 cup',
  strIngredient3: '',
  strMeasure3: '',
};

const mealDbHandler: Mock = (url) => {
  if (url.includes('/filter.php')) return json({ meals: [{ idMeal: '52772' }] });
  if (url.includes('/lookup.php') || url.includes('/search.php')) return json({ meals: [meal] });
  return json({ meals: null });
};

const allHealthy: Mock = (url, init) => {
  if (url.includes('themealdb.com')) return mealDbHandler(url, init);
  if (url.includes('/complexSearch')) return json({ results: [spoonRecipe] });
  if (url.includes('/information')) return json(spoonRecipe);
  throw new Error(`Unexpected call: ${url}`);
};

async function registerUser(prefix = 'user') {
  const email = `${prefix}-${crypto.randomUUID()}@test.dev`;
  const password = 'password123';
  const res = await request(app).post('/api/auth/register').send({ name: 'Tester', email, password }).expect(201);
  return { email, password, ...(res.body as { accessToken: string; refreshToken: string }) };
}

const authed = (token: string) => ({ Authorization: `Bearer ${token}` });

beforeAll(() => {
  globalThis.fetch = vi.fn((input: string | URL | Request, init?: RequestInit) => {
    const url = String(input);
    // supertest talks to the local app; only external traffic is intercepted.
    if (url.startsWith('http://127.0.0.1')) return realFetch(input, init);
    calls.push(url);
    return Promise.resolve(upstream(url, init));
  }) as typeof fetch;
});

beforeEach(() => {
  cache.clear();
  calls.length = 0;
  resetSpoonacularQuota();
  env.SPOONACULAR_API_KEY = 'test-spoonacular-key';
  env.GEMINI_API_KEY = 'test-gemini-key';
  upstream = allHealthy;
});

afterAll(async () => {
  globalThis.fetch = realFetch;
  await prisma.$disconnect();
});

describe('authentication', () => {
  it('registers, rejects duplicates and bad passwords, and hashes with bcrypt', async () => {
    const user = await registerUser();
    await request(app).post('/api/auth/register').send({ name: 'x', email: user.email, password: 'password123' }).expect(409);
    await request(app).post('/api/auth/register').send({ name: 'x', email: 'a@b.dev', password: 'short' }).expect(400);

    const stored = await prisma.user.findUniqueOrThrow({ where: { email: user.email } });
    expect(stored.passwordHash).toMatch(/^\$2[aby]\$12\$/);
  });

  it('logs in and rejects wrong passwords with the same error as unknown emails', async () => {
    const user = await registerUser();
    await request(app).post('/api/auth/login').send({ email: user.email, password: user.password }).expect(200);
    const wrong = await request(app).post('/api/auth/login').send({ email: user.email, password: 'nope-nope' }).expect(401);
    const unknown = await request(app).post('/api/auth/login').send({ email: 'ghost@test.dev', password: 'nope-nope' }).expect(401);
    expect(wrong.body).toEqual(unknown.body);
  });

  it('requires a valid token on protected routes', async () => {
    await request(app).get('/api/users/me').expect(401);
    await request(app).get('/api/users/me').set(authed('garbage')).expect(401);
  });

  it('rotates refresh tokens and treats replay as theft', async () => {
    const user = await registerUser();
    const first = await request(app).post('/api/auth/refresh').send({ refreshToken: user.refreshToken }).expect(200);
    expect(first.body.refreshToken).not.toBe(user.refreshToken);

    await request(app).post('/api/auth/refresh').send({ refreshToken: user.refreshToken }).expect(401);
    await request(app).post('/api/auth/refresh').send({ refreshToken: first.body.refreshToken }).expect(401);
  });

  it('logout revokes the refresh token', async () => {
    const user = await registerUser();
    await request(app).post('/api/auth/logout').send({ refreshToken: user.refreshToken }).expect(204);
    await request(app).post('/api/auth/refresh').send({ refreshToken: user.refreshToken }).expect(401);
  });

  it('resets a password with a single-use token and ends sessions', async () => {
    const user = await registerUser();
    const known = await request(app).post('/api/auth/forgot-password').send({ email: user.email }).expect(200);
    const unknown = await request(app).post('/api/auth/forgot-password').send({ email: 'ghost@test.dev' }).expect(200);
    expect(known.body).toEqual(unknown.body);

    const { hashToken } = await import('../src/lib/tokens');
    const token = crypto.randomUUID();
    const dbUser = await prisma.user.findUniqueOrThrow({ where: { email: user.email } });
    await prisma.passwordResetToken.create({
      data: { userId: dbUser.id, tokenHash: hashToken(token), expiresAt: new Date(Date.now() + 60_000) },
    });

    await request(app).post('/api/auth/reset-password').send({ token, password: 'newpassword1' }).expect(200);
    await request(app).post('/api/auth/reset-password').send({ token, password: 'another-pass1' }).expect(400);
    await request(app).post('/api/auth/login').send({ email: user.email, password: 'newpassword1' }).expect(200);
    await request(app).post('/api/auth/refresh').send({ refreshToken: user.refreshToken }).expect(401);
  });

  it('reports Google sign-in as unconfigured rather than pretending', async () => {
    env.GOOGLE_CLIENT_ID = undefined;
    const res = await request(app).post('/api/auth/google').send({ idToken: 'x' }).expect(503);
    expect(res.body.error.code).toBe('NOT_CONFIGURED');
  });
});

describe('pantry', () => {
  it('normalises Indonesian names, merges duplicates, and keeps the earliest expiry', async () => {
    const { accessToken } = await registerUser();
    const headers = authed(accessToken);

    const first = await request(app)
      .post('/api/pantry')
      .set(headers)
      .send({ name: 'telur', quantity: 6, unit: 'butir', expiresAt: '2030-01-10' })
      .expect(201);
    expect(first.body.nameEn).toBe('egg');
    expect(first.body.category.slug).toBe('dairy-eggs');

    const merged = await request(app)
      .post('/api/pantry')
      .set(headers)
      .send({ name: 'Egg', quantity: 4, expiresAt: '2030-01-05' })
      .expect(201);
    expect(merged.body.id).toBe(first.body.id);
    expect(merged.body.quantity).toBe(10);
    expect(merged.body.expiresAt).toBe('2030-01-05');

    await request(app).put(`/api/pantry/${first.body.id}`).set(headers).send({ quantity: 2, notes: 'kulkas' }).expect(200);
    const list = await request(app).get('/api/pantry?q=tel').set(headers).expect(200);
    expect(list.body).toHaveLength(1);
    expect(list.body[0].notes).toBe('kulkas');

    await request(app).delete(`/api/pantry/${first.body.id}`).set(headers).expect(204);
    expect((await request(app).get('/api/pantry').set(headers)).body).toHaveLength(0);
  });

  it('keeps one user out of another user\'s data', async () => {
    const owner = await registerUser('owner');
    const intruder = await registerUser('intruder');
    const item = await request(app).post('/api/pantry').set(authed(owner.accessToken)).send({ name: 'tahu' }).expect(201);

    await request(app).put(`/api/pantry/${item.body.id}`).set(authed(intruder.accessToken)).send({ quantity: 9 }).expect(404);
    await request(app).delete(`/api/pantry/${item.body.id}`).set(authed(intruder.accessToken)).expect(404);
    expect((await request(app).get('/api/pantry').set(authed(intruder.accessToken))).body).toHaveLength(0);
    expect((await request(app).get('/api/pantry').set(authed(owner.accessToken))).body).toHaveLength(1);

    const lists = await request(app).get('/api/shopping-lists').set(authed(owner.accessToken));
    await request(app).delete(`/api/shopping-lists/${lists.body[0].id}`).set(authed(intruder.accessToken)).expect(404);
  });

  it('groups items by expiry status', async () => {
    const { accessToken } = await registerUser();
    const headers = authed(accessToken);
    const { formatDate, addDays, today } = await import('../src/lib/dates');
    await request(app).post('/api/pantry/bulk').set(headers).send({
      items: [
        { name: 'bayam', expiresAt: formatDate(addDays(today(), -1)) },
        { name: 'tomat', expiresAt: formatDate(today()) },
        { name: 'wortel', expiresAt: formatDate(addDays(today(), 2)) },
        { name: 'kentang', expiresAt: formatDate(addDays(today(), 30)) },
      ],
    }).expect(201);
    const res = await request(app).get('/api/pantry/expiring').set(headers).expect(200);
    expect(res.body.expired.map((i: { nameEn: string }) => i.nameEn)).toEqual(['spinach']);
    expect(res.body.expiringToday.map((i: { nameEn: string }) => i.nameEn)).toEqual(['tomato']);
    expect(res.body.expiringSoon.map((i: { nameEn: string }) => i.nameEn)).toEqual(['carrot']);
  });

  it('offers autocomplete on Indonesian, English and alias names', async () => {
    const { accessToken } = await registerUser();
    const headers = authed(accessToken);
    for (const [q, expected] of [['bawang', 'shallot'], ['chick', 'chicken'], ['cabe', 'chili']] as const) {
      const res = await request(app).get('/api/ingredients').query({ q }).set(headers).expect(200);
      expect(res.body.map((i: { nameEn: string }) => i.nameEn)).toContain(expected);
    }
  });
});

describe('recipe orchestration', () => {
  async function userWithPantry(names: string[]) {
    const user = await registerUser();
    await request(app).post('/api/pantry/bulk').set(authed(user.accessToken)).send({ items: names.map((name) => ({ name })) }).expect(201);
    return user;
  }

  it('calculates matches and treats garnish as optional', async () => {
    const { accessToken } = await userWithPantry(['telur', 'tomat']);
    const res = await request(app).get('/api/recipes/by-ingredients').set(authed(accessToken)).expect(200);
    const hit = res.body.recipes.find((r: { key: string }) => r.key === 'spoonacular-716429');
    expect(hit.matchPercent).toBe(100);
    expect(hit.missing).toEqual([]);
    expect(hit.optionalMissing).toEqual(['parsley']);
  });

  it('never sends the API key in a URL', async () => {
    const { accessToken } = await userWithPantry(['telur']);
    await request(app).get('/api/recipes/recommendations').set(authed(accessToken)).expect(200);
    expect(calls.some((c) => c.includes('spoonacular.com'))).toBe(true);
    expect(calls.some((c) => c.includes('test-spoonacular-key'))).toBe(false);
  });

  it('falls back to TheMealDB when Spoonacular fails', async () => {
    upstream = (url, init) => (url.includes('spoonacular') ? json({}, 500) : mealDbHandler(url, init));
    const { accessToken } = await userWithPantry(['ayam']);
    const res = await request(app).get('/api/recipes/recommendations').set(authed(accessToken)).expect(200);
    expect(res.body.sources).toEqual({ spoonacular: 'failed', themealdb: 'ok' });
    expect(res.body.recipes[0].key).toBe('mealdb-52772');
  });

  it('uses Spoonacular results when TheMealDB fails', async () => {
    upstream = (url, init) => (url.includes('themealdb') ? json({}, 500) : allHealthy(url, init));
    const { accessToken } = await userWithPantry(['telur']);
    const res = await request(app).get('/api/recipes/recommendations').set(authed(accessToken)).expect(200);
    expect(res.body.sources).toEqual({ spoonacular: 'ok', themealdb: 'failed' });
    expect(res.body.recipes).toHaveLength(1);
  });

  it('suggests AI when both recipe APIs fail', async () => {
    upstream = () => json({}, 503);
    const { accessToken } = await userWithPantry(['telur']);
    const res = await request(app).get('/api/recipes/recommendations').set(authed(accessToken)).expect(200);
    expect(res.body.suggestAi).toBe(true);
    expect(res.body.recipes).toEqual([]);
  });

  it('stops calling Spoonacular after the daily quota is spent', async () => {
    upstream = (url, init) => (url.includes('spoonacular') ? json({}, 402) : mealDbHandler(url, init));
    const first = await userWithPantry(['telur']);
    await request(app).get('/api/recipes/recommendations').set(authed(first.accessToken)).expect(200);
    const spent = calls.filter((c) => c.includes('spoonacular')).length;
    expect(spent).toBe(1);

    const second = await userWithPantry(['ayam']);
    const res = await request(app).get('/api/recipes/recommendations').set(authed(second.accessToken)).expect(200);
    expect(calls.filter((c) => c.includes('spoonacular'))).toHaveLength(spent);
    expect(res.body.sources.spoonacular).toBe('skipped');
  });

  it('skips TheMealDB when Spoonacular already gave enough good results', async () => {
    const results = Array.from({ length: 7 }, (_, i) => ({ ...spoonRecipe, id: i + 1, title: `Egg Dish ${i}` }));
    upstream = (url) => {
      if (url.includes('complexSearch')) return json({ results });
      throw new Error('TheMealDB should not be called');
    };
    const { accessToken } = await userWithPantry(['telur', 'tomat']);
    const res = await request(app).get('/api/recipes/recommendations').set(authed(accessToken)).expect(200);
    expect(res.body.sources.themealdb).toBe('skipped');
  });

  it('caches repeat searches', async () => {
    const { accessToken } = await userWithPantry(['telur']);
    await request(app).get('/api/recipes/recommendations').set(authed(accessToken)).expect(200);
    const firstRun = calls.length;
    await request(app).get('/api/recipes/recommendations').set(authed(accessToken)).expect(200);
    expect(calls.length).toBe(firstRun);
  });

  it('applies availability filters', async () => {
    const { accessToken } = await userWithPantry(['telur']);
    const headers = authed(accessToken);
    const onlyAvailable = await request(app).get('/api/recipes/by-ingredients?mode=available').set(headers).expect(200);
    expect(onlyAvailable.body.recipes).toEqual([]);
    const minMatch = await request(app).get('/api/recipes/by-ingredients?minMatch=40').set(headers).expect(200);
    expect(minMatch.body.recipes.map((r: { key: string }) => r.key)).toContain('spoonacular-716429');
    const strict = await request(app).get('/api/recipes/by-ingredients?minMatch=90').set(headers).expect(200);
    expect(strict.body.recipes).toEqual([]);
  });

  it('serves detail with availability, stores it, and survives an outage', async () => {
    const { accessToken } = await userWithPantry(['telur']);
    const headers = authed(accessToken);
    const detail = await request(app).get('/api/recipes/spoonacular-716429').set(headers).expect(200);
    expect(detail.body.nutrition.calories).toBe(210);
    expect(detail.body.instructions[1].timerSeconds).toBe(300);
    expect(detail.body.ingredients.find((i: { name: string }) => i.name === 'egg').available).toBe(true);
    expect(detail.body.ingredients.find((i: { name: string }) => i.name === 'tomato').available).toBe(false);

    upstream = () => json({}, 500);
    await prisma.recipe.update({ where: { key: 'spoonacular-716429' }, data: { fetchedAt: new Date(0) } });
    await request(app).get('/api/recipes/spoonacular-716429').set(headers).expect(200);

    const recent = await request(app).get('/api/recipes/recently-viewed').set(headers).expect(200);
    expect(recent.body[0].key).toBe('spoonacular-716429');
  });

  it('rejects malformed recipe ids', async () => {
    const { accessToken } = await registerUser();
    await request(app).get('/api/recipes/evil-..%2F..').set(authed(accessToken)).expect(400);
  });

  it('keeps nutrition null for TheMealDB rather than inventing it', async () => {
    const { accessToken } = await registerUser();
    const res = await request(app).get('/api/recipes/mealdb-52772').set(authed(accessToken)).expect(200);
    expect(res.body.nutrition).toBeNull();
    expect(res.body.ingredients[1]).toMatchObject({ name: 'soy sauce', quantity: 0.75, unit: 'cup' });
  });
});

const generated = {
  recipe_name: 'Telur Ceplok',
  description: 'Telur goreng sederhana.',
  ingredients: [
    { name: 'Telur', name_en: 'egg', quantity: 1, unit: 'butir', available: true, required: true },
    { name: 'Minyak goreng', name_en: 'cooking oil', quantity: 1, unit: 'sdt', available: false, required: true },
  ],
  instructions: ['Panaskan minyak.', 'Goreng telur selama 3 menit.'],
  cooking_time: 5,
  servings: 1,
  difficulty: 'Easy',
  nutrition: { calories: null, protein: null, carbohydrates: null, fat: null },
};

describe('AI', () => {
  const jpeg = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(64)]);

  it('scans an image without saving to the pantry', async () => {
    upstream = () =>
      geminiReply({
        image_quality: 'good',
        ingredients: [
          { name: 'Egg', name_id: 'Telur', confidence: 0.98, quantity: null, unit: 'pieces' },
          { name: 'Mystery', name_id: null, confidence: 0.1, quantity: null, unit: null },
        ],
      });
    const { accessToken } = await registerUser();
    const headers = authed(accessToken);
    const res = await request(app)
      .post('/api/ai/scan-ingredients')
      .set(headers)
      .attach('image', jpeg, { filename: 'a.jpg', contentType: 'image/jpeg' })
      .expect(200);
    expect(res.body.ingredients.map((i: { name: string }) => i.name)).toEqual(['Egg']);
    expect((await request(app).get('/api/pantry').set(headers)).body).toHaveLength(0);
    expect((await request(app).get('/api/history').set(headers)).body).toHaveLength(1);
  });

  it('explains blurry images', async () => {
    upstream = () => geminiReply({ image_quality: 'blurry', ingredients: [] });
    const { accessToken } = await registerUser();
    const res = await request(app)
      .post('/api/ai/scan-ingredients')
      .set(authed(accessToken))
      .attach('image', jpeg, { filename: 'a.jpg', contentType: 'image/jpeg' })
      .expect(200);
    expect(res.body.message).toContain('kurang jelas');
  });

  it('rejects non-images even with a spoofed type, and oversized files', async () => {
    const { accessToken } = await registerUser();
    const headers = authed(accessToken);
    await request(app)
      .post('/api/ai/scan-ingredients')
      .set(headers)
      .attach('image', Buffer.from('<script>alert(1)</script>'), { filename: 'a.jpg', contentType: 'image/jpeg' })
      .expect(415);
    await request(app)
      .post('/api/ai/scan-ingredients')
      .set(headers)
      .attach('image', Buffer.alloc(9 * 1024 * 1024), { filename: 'big.jpg', contentType: 'image/jpeg' })
      .expect(413);
    await request(app).post('/api/ai/scan-ingredients').set(headers).expect(400);
  });

  it('reports a Gemini failure so the app can offer manual entry', async () => {
    upstream = () => json({ error: 'bad' }, 400);
    const { accessToken } = await registerUser();
    const res = await request(app)
      .post('/api/ai/scan-ingredients')
      .set(authed(accessToken))
      .attach('image', jpeg, { filename: 'a.jpg', contentType: 'image/jpeg' })
      .expect(502);
    expect(res.body.error.code).toBe('UPSTREAM_FAILED');
  });

  it('reports a missing Gemini key as unconfigured', async () => {
    env.GEMINI_API_KEY = undefined;
    const { accessToken } = await registerUser();
    const res = await request(app).post('/api/ai/generate-recipe').set(authed(accessToken)).send({ ingredients: ['telur'] }).expect(503);
    expect(res.body.error.code).toBe('NOT_CONFIGURED');
  });

  it('generates a labelled recipe, recomputes availability, and keeps it private', async () => {
    upstream = () => geminiReply({ recipes: [generated] });
    const owner = await registerUser();
    const res = await request(app).post('/api/ai/generate-recipe').set(authed(owner.accessToken)).send({ ingredients: ['telur'] }).expect(201);
    const [recipe] = res.body.recipes;
    expect(recipe.isAiGenerated).toBe(true);
    expect(recipe.nutrition).toBeNull();
    expect(recipe.match.available).toEqual(['Telur']);
    expect(recipe.match.missing).toEqual(['Minyak goreng']);

    await request(app).get(`/api/recipes/${recipe.key}`).set(authed(owner.accessToken)).expect(200);
    const other = await registerUser();
    await request(app).get(`/api/recipes/${recipe.key}`).set(authed(other.accessToken)).expect(404);
  });

  it('rejects malformed AI output instead of showing it', async () => {
    upstream = () => geminiReply({ recipes: [{ recipe_name: 'x' }] });
    const { accessToken } = await registerUser();
    const res = await request(app).post('/api/ai/generate-recipe').set(authed(accessToken)).send({ ingredients: ['telur'] }).expect(502);
    expect(res.body.error.code).toBe('AI_INVALID_RESPONSE');
  });

  it('blocks recipes containing the user allergens', async () => {
    upstream = () => geminiReply({ recipes: [generated] });
    const { accessToken } = await registerUser();
    await request(app).put('/api/users/preferences').set(authed(accessToken)).send({ allergies: ['egg'] }).expect(200);
    const res = await request(app).post('/api/ai/generate-recipe').set(authed(accessToken)).send({ ingredients: ['telur'] }).expect(422);
    expect(res.body.error.code).toBe('AI_ALLERGEN_REJECTED');
  });

  it('returns substitutions and sends no key in the URL', async () => {
    upstream = () =>
      geminiReply({
        substitutions: [
          {
            missing_ingredient: 'Butter',
            substitute: 'Minyak',
            how_to_substitute: 'Gunakan 3/4 takaran.',
            taste_texture_impact: 'Kurang gurih.',
            suitability: 'acceptable',
            safety_note: null,
          },
        ],
      });
    const { accessToken } = await registerUser();
    const res = await request(app).post('/api/ai/substitute-ingredients').set(authed(accessToken)).send({ missing: ['Butter'], available: ['Minyak'] }).expect(200);
    expect(res.body.substitutions[0].suitability).toBe('acceptable');
    expect(calls.some((c) => c.includes('test-gemini-key'))).toBe(false);
  });
});

describe('favorites, shopping lists and meal plans', () => {
  it('saves, organises, searches and removes favorites', async () => {
    const { accessToken } = await registerUser();
    const headers = authed(accessToken);
    const collection = await request(app).post('/api/collections').set(headers).send({ name: 'Malam' }).expect(201);
    const favorite = await request(app)
      .post('/api/favorites')
      .set(headers)
      .send({ recipeKey: 'mealdb-52772', collectionId: collection.body.id })
      .expect(201);

    const found = await request(app).get('/api/favorites?q=Teriyaki').set(headers).expect(200);
    expect(found.body[0].collectionName).toBe('Malam');
    expect((await request(app).get('/api/favorites?q=zzz').set(headers)).body).toHaveLength(0);

    const detail = await request(app).get('/api/recipes/mealdb-52772').set(headers).expect(200);
    expect(detail.body.favoriteId).toBe(favorite.body.id);

    await request(app).delete(`/api/favorites/${favorite.body.id}`).set(headers).expect(204);
    expect((await request(app).get('/api/favorites').set(headers)).body).toHaveLength(0);
  });

  it('adds missing ingredients without duplicates and tracks purchases', async () => {
    const { accessToken } = await registerUser();
    const headers = authed(accessToken);
    const [list] = (await request(app).get('/api/shopping-lists').set(headers)).body;
    expect(list.name).toBe('Daftar Belanja');

    await request(app)
      .post(`/api/shopping-lists/${list.id}/items`)
      .set(headers)
      .send({ items: [{ name: 'Beras', quantity: 1, unit: 'kg', recipeKey: 'mealdb-52772' }, { name: 'beras' }] })
      .expect(201);
    const again = await request(app).post(`/api/shopping-lists/${list.id}/items`).set(headers).send({ items: [{ name: 'BERAS' }] }).expect(201);
    expect(again.body.items).toHaveLength(1);

    const itemId = again.body.items[0].id;
    await request(app).put(`/api/shopping-lists/${list.id}/items/${itemId}`).set(headers).send({ isPurchased: true, quantity: 2 }).expect(200);
    await request(app).delete(`/api/shopping-lists/${list.id}/items/${itemId}`).set(headers).expect(204);
  });

  it('fills item categories from the catalog', async () => {
    const { accessToken } = await registerUser();
    const headers = authed(accessToken);
    const [list] = (await request(app).get('/api/shopping-lists').set(headers)).body;
    const res = await request(app)
      .post(`/api/shopping-lists/${list.id}/items`)
      .set(headers)
      .send({ items: [{ name: 'soy sauce' }, { name: 'Bawang Putih' }, { name: 'unobtainium' }] })
      .expect(201);
    const category = (name: string) => res.body.items.find((i: { name: string }) => i.name === name).category;
    expect(category('soy sauce')).toBe('Saus & Minyak');
    expect(category('Bawang Putih')).toBe('Sayuran');
    expect(category('unobtainium')).toBe('Lainnya');
  });

  it('plans a week, replaces a slot, and removes meals', async () => {
    const { accessToken } = await registerUser();
    const headers = authed(accessToken);
    const body = { recipeKey: 'mealdb-52772', date: '2030-07-03', mealType: 'DINNER' };
    const first = await request(app).post('/api/meal-plans').set(headers).send(body).expect(201);
    const replaced = await request(app).post('/api/meal-plans').set(headers).send({ ...body, servings: 4 }).expect(201);
    expect(replaced.body.id).toBe(first.body.id);

    const week = await request(app).get('/api/meal-plans?weekStart=2030-07-05').set(headers).expect(200);
    expect(week.body.weekStart).toBe('2030-07-01');
    expect(week.body.items).toHaveLength(1);
    expect(week.body.items[0].servings).toBe(4);

    await request(app).delete(`/api/meal-plans/${first.body.id}`).set(headers).expect(204);
    expect((await request(app).get('/api/meal-plans?weekStart=2030-07-05').set(headers)).body.items).toHaveLength(0);
  });
});
