import { PrismaClient } from '@prisma/client';
import { CATALOG, CATEGORIES, ingredientSearchText } from '../src/data/catalog';

const prisma = new PrismaClient();

async function main() {
  const categoryIds = new Map<string, number>();
  for (const [sortOrder, category] of CATEGORIES.entries()) {
    const row = await prisma.ingredientCategory.upsert({
      where: { slug: category.slug },
      create: { ...category, sortOrder },
      update: { ...category, sortOrder },
    });
    categoryIds.set(category.slug, row.id);
  }

  for (const [nameEn, nameId, slug, defaultUnit, aliases = []] of CATALOG) {
    const data = {
      nameId,
      aliases,
      searchText: ingredientSearchText(nameEn, nameId, aliases),
      defaultUnit,
      categoryId: categoryIds.get(slug) ?? 0,
    };
    await prisma.ingredient.upsert({
      where: { nameEn_scope: { nameEn, scope: 'global' } },
      create: { nameEn, scope: 'global', ...data },
      update: data,
    });
  }
  console.log(`Seeded ${CATEGORIES.length} categories and ${CATALOG.length} ingredients`);
}

main()
  .catch((err) => {
    console.error(err);
    process.exitCode = 1;
  })
  .finally(() => prisma.$disconnect());
