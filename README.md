# BelalangV

Aplikasi Android (Kotlin + Jetpack Compose) untuk memaparkan senarai booking sebagai job cards dengan carian, filter, sorting dan auto-refresh. Konfigurasi API dibaca dari **Cloud Firestore** (`api_configs`) supaya boleh diubah tanpa membina semula APK. Lihat `docs/FIRESTORE.md` dan `AGENTS.md`.

## Aliran
1. Pilih jenis servis: Tambang ke KLIA / Normal.
2. Tapisan Step 1: jenis servis, tambang (min/maks), jam pickup. Criteria Step 2: lokasi, jarak, lokasi dielakkan. Carian dan sorting. Auto-fetch bila app dibuka; auto-refresh (min 5s, lalai OFF).
3. Step 3 Accept dan Step 4 Confirm: **hanya apabila pengguna menekan butang** dan mengesahkan dialog. Step 4 disabled secara lalai.

## Status
Lihat bahagian "Belum siap" di bawah.

**Siap (kod ditulis):** konfigurasi Firestore, pelaksana request generik, parser/penapis/sorting, UI Compose, ujian unit, CI.

**Belum siap / diperlukan dari anda:**
- Projek Firebase + `google-services.json` (letak di `app/` secara setempat, atau GitHub Secret `GOOGLE_SERVICES_JSON_B64` untuk CI).
- Butiran request sebenar untuk step1-4 (URL, method, header, body, bentuk respons) dalam Firestore. Tanpa `fieldMap`/`listPath`, senarai job kosong dengan mesej ralat (app tidak meneka format).
- **Kaedah pengesahan API belum ditentukan.** Tiada token disimpan dalam Firestore atau kod; belum ada mekanisme pengesahan dalam app.
- Skema deeplink (`jobId`, `jobType`): kod membaca parameter itu jika app dibuka dengan URI berkenaan, tetapi tiada `intent-filter` ditambah kerana skema belum diberi.
- Ujian pada peranti dan pengesahan build: lihat keputusan CI.

## Build
```
gradle :app:testDebugUnitTest :app:assembleDebug
```
Signing release: env `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (jangan commit).
