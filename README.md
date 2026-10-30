# SmartPantry AI

Asisten memasak berbasis AI untuk Android: scan bahan dengan kamera, kelola pantry, temukan resep dari
Spoonacular dan TheMealDB, buat resep dengan Gemini, dan atur belanja serta rencana makan.

| Bagian | Teknologi | Lokasi |
| --- | --- | --- |
| Aplikasi Android | Kotlin, Jetpack Compose, Material 3, MVVM, Retrofit, CameraX, WorkManager | `app/` |
| Backend | Node.js, Express 5, TypeScript, Zod, Prisma, JWT | `backend/` |
| Database | MySQL 8 | `backend/prisma/` |

Aplikasi hanya berbicara dengan backend. API key disimpan di `backend/.env` dan tidak pernah masuk ke APK.

## Prasyarat

- Node.js 20 atau lebih baru
- MySQL 8 (di mesin ini service `MySQL84` berjalan di port **3307**)
- Android Studio dengan SDK 36 dan emulator atau HP Android

## 1. Menjalankan backend

```powershell
cd backend
npm install
copy .env.example .env
```

Buat database, lalu isi `backend/.env`:

```sql
CREATE DATABASE smartpantry CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

| Variabel | Wajib | Keterangan |
| --- | --- | --- |
| `DATABASE_URL` | Ya | `mysql://USER:PASSWORD@127.0.0.1:3307/smartpantry` |
| `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET` | Ya | Minimal 32 karakter dan berbeda satu sama lain |
| `GEMINI_API_KEY` | Untuk fitur AI | Dari [Google AI Studio](https://aistudio.google.com/apikey) |
| `SPOONACULAR_API_KEY` | Disarankan | Dari [Spoonacular](https://spoonacular.com/food-api/console) |
| `THEMEALDB_API_KEY` | Tidak | Bawaan `1`, key uji publik TheMealDB |
| `GOOGLE_CLIENT_ID` | Untuk login Google | OAuth client bertipe *Web* |
| `SMTP_*` | Untuk produksi | Tanpa SMTP, kode reset password dicetak ke log server saat development |

Buat secret dengan:

```powershell
node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"
```

Lalu siapkan tabel dan katalog bahan, dan jalankan server:

```powershell
npm run db:deploy
npm run db:seed
npm run dev
```

Buka <http://localhost:4000/health>. Bagian `services` menunjukkan API mana yang sudah aktif.

Tanpa key, fitur terkait menjawab `503 NOT_CONFIGURED` dan aplikasi menampilkan pesannya. Tidak ada data palsu.

## 2. Menjalankan aplikasi Android

1. Buka folder proyek di Android Studio, lalu Sync Gradle.
2. Pastikan backend berjalan.
3. Klik Run.

**Emulator** tidak perlu pengaturan: aplikasi memakai `http://10.0.2.2:4000/`.

**HP asli** harus satu Wi-Fi dengan laptop. Tambahkan IP laptop ke `local.properties`:

```properties
smartpantry.apiBaseUrl=http://192.168.1.10:4000/
```

**Login Google** membutuhkan tiga hal di Google Cloud Console:

1. OAuth client *Web*: isi ID-nya ke `GOOGLE_CLIENT_ID` di `backend/.env` dan ke `local.properties`:
   ```properties
   smartpantry.googleWebClientId=xxxxx.apps.googleusercontent.com
   ```
2. OAuth client *Android* dengan package `com.example.mts` dan SHA-1 dari `./gradlew signingReport`.
3. Consent screen yang sudah dikonfigurasi.

Build dari terminal:

```powershell
.\gradlew.bat :app:assembleDebug
```

Build rilis harus memakai backend HTTPS, karena trafik HTTP biasa hanya diizinkan di build debug
(`app/src/debug/AndroidManifest.xml`).

## Pengujian

```powershell
# Backend. Butuh DATABASE_URL ke database uji yang sudah dimigrasi dan di-seed.
cd backend
npm test

# Android: unit test dan contract test
.\gradlew.bat :app:testDebugUnitTest
```

`backend/tests/contract.test.ts` merekam respons API ke `app/src/test/resources/contract/`, lalu `ContractTest.kt`
men-decode rekaman itu. Jika struktur respons berubah, jalankan tes backend lalu tes Android.

## Arsitektur

Struktur `backend/src`:

| Folder | Isi |
| --- | --- |
| `modules/` | Route per fitur |
| `services/orchestrator.ts` | Memilih API, fallback, gabung hasil, hapus duplikat, peringkat |
| `services/matching.ts` | Pencocokan bahan dan persentase kecocokan |
| `services/normalizer.ts` | Nama Indonesia ke Inggris, dari katalog lalu Gemini |
| `services/recipeStore.ts` | Cache resep di MySQL |
| `services/gemini.ts`, `spoonacular.ts`, `themealdb.ts` | Integrasi API luar |

Alur pencarian resep:

1. Spoonacular dipanggil lebih dulu jika tersedia dan kuotanya masih ada.
2. TheMealDB hanya dipanggil jika hasil yang cocok minimal 50% kurang dari 6.
3. Hasil digabung, duplikat dihapus, lalu diurutkan berdasarkan kecocokan. Resep yang memakai bahan hampir
   kedaluwarsa mendapat prioritas.
4. Jika kedua sumber gagal, respons berisi `suggestAi: true` dan aplikasi menawarkan resep Gemini.

Aturan pencocokan:

- Persentase dihitung dari bahan wajib saja. Bahan bertanda optional, garnish, atau secukupnya, serta air, tidak ikut.
- Bahan kedaluwarsa tidak dihitung tersedia.
- `milk` tidak cocok dengan `coconut milk`, dan `tomato` tidak cocok dengan `tomato sauce`.

Perlindungan API: timeout per permintaan, retry dengan exponential backoff untuk 429/5xx, cache memori, rate limiting,
dan Spoonacular berhenti dipanggil sampai reset harian UTC setelah kuota habis (HTTP 402).

## Dokumentasi API

Lihat [backend/API.md](backend/API.md).

## Bahasa

Teks aplikasi ada di `app/src/main/res/values/strings.xml` (Indonesia). Untuk menambah bahasa Inggris, buat
`values-en/strings.xml`. Pesan error berbahasa Indonesia berasal dari backend.
