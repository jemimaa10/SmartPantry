import { describe, expect, it } from 'vitest';
import { addDays, parseDate, today, weekStartOf } from '../src/lib/dates';
import { expiryStatus } from '../src/modules/pantry';
import { computeMatch, dedupeKey, ingredientsMatch, isOptionalIngredient } from '../src/services/matching';
import { lookupCatalog, translateQuery } from '../src/services/normalizer';
import { mergeAndRank } from '../src/services/orchestrator';
import { timerFromText, type RecipeData, type RecipeIngredientData } from '../src/services/recipeTypes';
import { parseMeasure } from '../src/services/themealdb';

const ingredient = (name: string, required = true): RecipeIngredientData => ({
  name,
  displayName: null,
  original: null,
  quantity: null,
  unit: null,
  required,
});

const recipe = (key: string, title: string, names: string[]): RecipeData => ({
  key,
  source: 'MEALDB',
  externalId: key,
  title,
  description: null,
  imageUrl: null,
  sourceUrl: null,
  cookingMinutes: null,
  servings: null,
  difficulty: null,
  cuisine: null,
  category: null,
  nutrition: null,
  nutritionIsEstimate: false,
  ingredients: names.map((n) => ingredient(n)),
  instructions: [],
});

describe('ingredientsMatch', () => {
  it.each([
    ['egg', 'eggs'],
    ['tomato', 'tomatoes'],
    ['chicken', 'boneless skinless chicken breast'],
    ['chicken breast', 'chicken'],
    ['cheese', 'cheddar cheese'],
    ['onion', 'large onion'],
  ])('matches %s with %s', (a, b) => expect(ingredientsMatch(a, b)).toBe(true));

  it.each([
    ['milk', 'coconut milk'],
    ['coconut milk', 'milk'],
    ['tomato', 'tomato sauce'],
    ['garlic', 'garlic powder'],
    ['chicken', 'chicken stock'],
    ['butter', 'peanut butter'],
    ['egg', 'egg noodles'],
    ['rice', 'rice flour'],
    ['potato', 'sweet potato'],
    ['soy sauce', 'sweet soy sauce'],
    ['egg', 'eggplant'],
  ])('does not match %s with %s', (a, b) => expect(ingredientsMatch(a, b)).toBe(false));
});

describe('computeMatch', () => {
  it('does not report 100% while a required ingredient is missing', () => {
    const result = computeMatch([ingredient('egg'), ingredient('rice')], ['egg']);
    expect(result.matchPercent).toBe(50);
    expect(result.missing).toEqual(['rice']);
  });

  it('ignores optional ingredients in the percentage but reports them', () => {
    const result = computeMatch([ingredient('egg'), ingredient('parsley', false)], ['egg']);
    expect(result.matchPercent).toBe(100);
    expect(result.missing).toEqual([]);
    expect(result.optionalMissing).toEqual(['parsley']);
  });

  it('never reports a match for a recipe with no required ingredients', () => {
    expect(computeMatch([ingredient('parsley', false)], []).matchPercent).toBe(0);
    expect(computeMatch([], ['egg']).matchPercent).toBe(0);
  });

  it('flags expiring pantry items the recipe uses', () => {
    const result = computeMatch([ingredient('egg'), ingredient('rice')], ['egg', 'rice'], ['egg']);
    expect(result.usesExpiring).toEqual(['egg']);
  });
});

describe('isOptionalIngredient', () => {
  it('reads optional wording from the original line', () => {
    expect(isOptionalIngredient('parsley', 'chopped parsley, for garnish')).toBe(true);
    expect(isOptionalIngredient('salt', 'salt to taste')).toBe(true);
    expect(isOptionalIngredient('water')).toBe(true);
    expect(isOptionalIngredient('egg', '2 large eggs')).toBe(false);
  });
});

describe('mergeAndRank', () => {
  it('dedupes the same dish across sources, keeping the better match', () => {
    const ranked = mergeAndRank(
      [recipe('a', 'Easy Tomato Omelette', ['egg', 'tomato', 'cheese']), recipe('b', 'Tomato Omelettes', ['egg', 'tomato'])],
      ['egg', 'tomato'],
      [],
    );
    expect(ranked).toHaveLength(1);
    expect(ranked[0]?.key).toBe('b');
  });

  it('sorts by match and lifts recipes that use expiring items', () => {
    const ranked = mergeAndRank(
      [recipe('low', 'Fried Rice', ['rice', 'egg']), recipe('high', 'Boiled Egg', ['egg'])],
      ['egg'],
      [],
    );
    expect(ranked.map((r) => r.key)).toEqual(['high', 'low']);

    const lifted = mergeAndRank(
      [recipe('fresh', 'Plain Toast', ['bread']), recipe('expiring', 'Spinach Egg', ['spinach', 'egg', 'cheese'])],
      ['bread', 'spinach', 'egg', 'cheese'],
      ['spinach'],
    );
    expect(lifted[0]?.key).toBe('expiring');
  });
});

describe('dedupeKey', () => {
  it('ignores case, filler words, plurals and word order', () => {
    expect(dedupeKey('The Best Easy Chicken Curry')).toBe(dedupeKey('curry chicken'));
  });
});

describe('normalizer', () => {
  it.each([
    ['Telur', 'egg'],
    ['bawang merah', 'shallot'],
    ['BAWANG PUTIH', 'garlic'],
    ['cabe', 'chili'],
    ['Ayam', 'chicken'],
    ['Tempe', 'tempeh'],
    ['Tahu', 'tofu'],
    ['kecap manis', 'sweet soy sauce'],
    ['Egg', 'egg'],
  ])('maps %s to %s', (input, expected) => expect(lookupCatalog(input)).toBe(expected));

  it('translates Indonesian search phrases', () => {
    expect(translateQuery('ayam goreng')).toBe('chicken fried');
    expect(translateQuery('sup bawang merah')).toBe('soup shallot');
    expect(translateQuery('pizza')).toBe('pizza');
  });
});

describe('parseMeasure', () => {
  it.each([
    ['3/4 cup', 0.75, 'cup'],
    ['1 1/2 tbs', 1.5, 'tbs'],
    ['200g', 200, 'g'],
    ['2', 2, null],
    ['Pinch', null, 'Pinch'],
    ['', null, null],
  ])('parses %s', (input, quantity, unit) => expect(parseMeasure(input)).toEqual({ quantity, unit }));
});

describe('timerFromText', () => {
  it('reads step durations in English and Indonesian', () => {
    expect(timerFromText('Bake for 20 minutes.')).toBe(1200);
    expect(timerFromText('Simmer 10-15 mins')).toBe(900);
    expect(timerFromText('Rebus selama 2 jam')).toBe(7200);
    expect(timerFromText('Goreng 30 detik')).toBe(30);
    expect(timerFromText('Stir well')).toBeNull();
  });
});

describe('dates', () => {
  it('uses the Jakarta calendar day', () => {
    expect(today(new Date('2026-10-30T18:00:00Z')).toISOString().slice(0, 10)).toBe('2026-10-31');
  });

  it('finds the Monday of a week', () => {
    expect(weekStartOf(parseDate('2026-11-01')).toISOString().slice(0, 10)).toBe('2026-10-26');
    expect(weekStartOf(parseDate('2026-10-26')).toISOString().slice(0, 10)).toBe('2026-10-26');
  });

  it('classifies expiry', () => {
    const now = parseDate('2026-10-30');
    expect(expiryStatus(null, 3, now)).toBe('NONE');
    expect(expiryStatus(addDays(now, -1), 3, now)).toBe('EXPIRED');
    expect(expiryStatus(now, 3, now)).toBe('EXPIRING_TODAY');
    expect(expiryStatus(addDays(now, 3), 3, now)).toBe('EXPIRING_SOON');
    expect(expiryStatus(addDays(now, 4), 3, now)).toBe('FRESH');
  });
});
