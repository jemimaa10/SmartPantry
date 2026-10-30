import type { RecipeSource } from '@prisma/client';

export interface RecipeIngredientData {
  name: string;
  displayName: string | null;
  original: string | null;
  quantity: number | null;
  unit: string | null;
  required: boolean;
}

export interface RecipeInstructionData {
  step: number;
  text: string;
  timerSeconds: number | null;
}

export interface NutritionData {
  calories: number | null;
  protein: number | null;
  carbohydrates: number | null;
  fat: number | null;
}

export interface RecipeData {
  key: string;
  source: RecipeSource;
  externalId: string;
  title: string;
  description: string | null;
  imageUrl: string | null;
  sourceUrl: string | null;
  cookingMinutes: number | null;
  servings: number | null;
  difficulty: string | null;
  cuisine: string | null;
  category: string | null;
  nutrition: NutritionData | null;
  nutritionIsEstimate: boolean;
  ingredients: RecipeIngredientData[];
  instructions: RecipeInstructionData[];
}

/** Reads a step's stated duration, e.g. "bake for 20 minutes", for the cooking timer. */
export function timerFromText(text: string): number | null {
  const match = /(\d+)(?:\s*(?:-|–|to)\s*(\d+))?\s*(hours?|hrs?|jam|minutes?|mins?|menit|seconds?|secs?|detik)\b/i.exec(text);
  if (!match) return null;
  const amount = Number(match[2] ?? match[1]);
  const unit = (match[3] ?? '').toLowerCase();
  if (/^(h|jam)/.test(unit)) return amount * 3600;
  if (/^(m)/.test(unit)) return amount * 60;
  return amount;
}
