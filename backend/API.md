# SmartPantry API

Base URL lokal: `http://localhost:4000`. Semua body berupa JSON, kecuali scan gambar.

Semua endpoint di luar `/health` dan `/api/auth/*` membutuhkan header `Authorization: Bearer <accessToken>`.

Bentuk error:

```json
{ "error": { "code": "VALIDATION_ERROR", "message": "Data yang dikirim tidak valid.", "details": [] } }
```

| Status | Kode | Arti |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR`, `INVALID_JSON`, `IMAGE_REQUIRED` | Input tidak valid |
| 401 | `UNAUTHORIZED` | Token tidak ada, salah, atau kedaluwarsa |
| 404 | `NOT_FOUND` | Data tidak ada atau bukan milik pengguna |
| 409 | `EMAIL_TAKEN`, `CONFLICT`, `INGREDIENT_IN_USE` | Bentrok dengan data yang ada |
| 413 | `UPLOAD_REJECTED` | Gambar lebih dari 8 MB |
| 415 | `UNSUPPORTED_IMAGE` | Bukan JPEG, PNG, atau WEBP |
| 422 | `AI_ALLERGEN_REJECTED` | Resep AI mengandung alergen pengguna |
| 429 | `RATE_LIMITED` | Terlalu banyak permintaan |
| 502 | `UPSTREAM_FAILED`, `AI_INVALID_RESPONSE` | API luar gagal |
| 503 | `NOT_CONFIGURED` | Key atau fitur belum dikonfigurasi |

## Auth

| Method | Path | Body | Hasil |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | `name`, `email`, `password` (8-72 karakter) | Sesi |
| POST | `/api/auth/login` | `email`, `password` | Sesi |
| POST | `/api/auth/google` | `idToken` | Sesi |
| POST | `/api/auth/refresh` | `refreshToken` | Sesi baru; token lama dicabut |
| POST | `/api/auth/logout` | `refreshToken` | 204 |
| POST | `/api/auth/forgot-password` | `email` | Pesan yang sama, terdaftar atau tidak |
| POST | `/api/auth/reset-password` | `token`, `password` | Pesan; semua sesi diakhiri |

Sesi berbentuk `{ accessToken, refreshToken, expiresIn }`. Memakai ulang refresh token lama mencabut semua sesi pengguna.

## Pengguna

| Method | Path | Body |
| --- | --- | --- |
| GET | `/api/users/me` | |
| PUT | `/api/users/me` | `displayName?`, `avatarUrl?`, `language?` |
| PUT | `/api/users/preferences` | `dietaryPreferences?`, `allergies?`, `dislikedIngredients?`, `notifications?` |

## Katalog bahan

| Method | Path | Keterangan |
| --- | --- | --- |
| GET | `/api/ingredients/categories` | Daftar kategori |
| GET | `/api/ingredients?q=&category=&limit=` | Autocomplete nama Indonesia, Inggris, dan alias |
| POST | `/api/ingredients` | `nameEn`, `nameId`, `categorySlug?`, `defaultUnit?`; bahan pribadi |
| PUT | `/api/ingredients/:id` | Hanya bahan pribadi |
| DELETE | `/api/ingredients/:id` | Hanya bahan pribadi yang tidak dipakai di pantry |

## Pantry

| Method | Path | Keterangan |
| --- | --- | --- |
| GET | `/api/pantry?q=&category=` | Terurut dari yang paling cepat kedaluwarsa |
| GET | `/api/pantry/expiring` | `{ expiringSoon, expiringToday, expired }` |
| POST | `/api/pantry` | `ingredientId` atau `name`, `quantity?`, `unit?`, `expiresAt?`, `notes?`, `categorySlug?` |
| POST | `/api/pantry/bulk` | `{ items: [...] }`, maksimal 50 |
| PUT | `/api/pantry/:id` | `quantity?`, `unit?`, `expiresAt?`, `notes?`; `null` menghapus nilainya |
| DELETE | `/api/pantry/:id` | |

Menambah bahan yang sudah ada akan menjumlahkan kuantitas dan menyimpan tanggal kedaluwarsa paling awal.
`expiryStatus` bernilai `FRESH`, `EXPIRING_SOON`, `EXPIRING_TODAY`, `EXPIRED`, atau `NONE`.

## AI

Dibatasi 15 permintaan per menit.

| Method | Path | Body |
| --- | --- | --- |
| POST | `/api/ai/scan-ingredients` | Multipart, field `image` |
| POST | `/api/ai/generate-recipe` | `ingredients?`, `maxMinutes?`, `servings?`, `category?`, `notes?`, `count?` (1-4) |
| POST | `/api/ai/substitute-ingredients` | `missing`, `available?`, `recipeTitle?` |

Hasil scan tidak masuk ke pantry. Aplikasi mengirim daftar yang sudah ditinjau pengguna ke `/api/pantry/bulk`.

```json
{
  "imageQuality": "good",
  "ingredients": [{ "name": "Egg", "name_id": "Telur", "confidence": 0.98, "quantity": null, "unit": "pieces" }],
  "message": null
}
```

## Resep

ID resep berbentuk `spoonacular-123`, `mealdb-52772`, atau `ai-<uuid>`.

| Method | Path | Query |
| --- | --- | --- |
| GET | `/api/recipes/search` | `q`, `minMatch?`, `mode?`, `maxMissing?` |
| GET | `/api/recipes/by-ingredients` | `ingredients?` (dipisah koma), `minMatch?`, `mode?`, `maxMissing?` |
| GET | `/api/recipes/recommendations` | `minMatch?`, `mode?`, `maxMissing?` |
| GET | `/api/recipes/recently-viewed` | |
| GET | `/api/recipes/:key` | |

`mode` bernilai `any`, `few` (kurang paling banyak `maxMissing` bahan), atau `available` (tidak kurang bahan wajib).

```json
{
  "recipes": [{
    "key": "mealdb-52772",
    "source": "MEALDB",
    "title": "Teriyaki Chicken Casserole",
    "matchPercent": 80,
    "available": ["chicken"],
    "missing": ["rice"],
    "optionalMissing": [],
    "usesExpiring": [],
    "isAiGenerated": false
  }],
  "sources": { "spoonacular": "ok", "themealdb": "skipped" },
  "suggestAi": false
}
```

`sources` bernilai `ok`, `failed`, atau `skipped`. Detail resep menambahkan `ingredients`, `instructions`, `nutrition`,
`nutritionIsEstimate`, `favoriteId`, dan `match`.

## Favorit dan koleksi

| Method | Path | Body atau query |
| --- | --- | --- |
| GET | `/api/favorites` | `q?`, `collectionId?` |
| POST | `/api/favorites` | `recipeKey`, `collectionId?` |
| PUT | `/api/favorites/:id` | `collectionId` |
| DELETE | `/api/favorites/:id` | |
| GET | `/api/collections` | |
| POST | `/api/collections` | `name` |
| PUT | `/api/collections/:id` | `name` |
| DELETE | `/api/collections/:id` | Resep tetap ada di favorit |

## Daftar belanja

| Method | Path | Body |
| --- | --- | --- |
| GET | `/api/shopping-lists` | |
| POST | `/api/shopping-lists` | `name`, `items?` |
| PUT | `/api/shopping-lists/:id` | `name` |
| DELETE | `/api/shopping-lists/:id` | |
| POST | `/api/shopping-lists/:id/items` | `{ items: [{ name, quantity?, unit?, category?, recipeKey? }] }` |
| PUT | `/api/shopping-lists/:id/items/:itemId` | `name?`, `quantity?`, `unit?`, `isPurchased?` |
| DELETE | `/api/shopping-lists/:id/items/:itemId` | |

Item yang belum dibeli dengan nama sama dilewati. Jika `category` kosong, nilainya diisi dari katalog atau `Lainnya`.

## Rencana makan

| Method | Path | Body atau query |
| --- | --- | --- |
| GET | `/api/meal-plans` | `weekStart?` (tanggal apa pun dalam minggu itu) |
| POST | `/api/meal-plans` | `recipeKey`, `date`, `mealType`, `servings?`; slot terisi akan diganti |
| PUT | `/api/meal-plans/:id` | `recipeKey`, `servings?` |
| DELETE | `/api/meal-plans/:id` | |

`mealType` bernilai `BREAKFAST`, `LUNCH`, atau `DINNER`.

## Riwayat scan

| Method | Path |
| --- | --- |
| GET | `/api/history` |
| DELETE | `/api/history/:id` |

Gambar tidak disimpan di server. Riwayat hanya berisi daftar bahan yang terdeteksi.
