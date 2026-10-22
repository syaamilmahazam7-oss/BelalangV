# AGENTS.md - BelalangV

Android (Kotlin + Jetpack Compose) untuk senarai booking melalui API dan tindakan Accept/Confirm manual.

## Peraturan wajib
1. **Accept (Step 3) dan Confirm (Step 4) hanya dihantar apabila pengguna menekan butang jelas dalam UI** (dengan dialog pengesahan). Tiada panggilan automatik, tiada gelung, tiada Accessibility/overlay/klik automatik.
2. **Jangan reka** URL, method, header, body atau format respons. Semuanya dari Firestore `api_configs`. Jika tiada, paparkan ralat/status yang jelas.
3. **Step 4 disabled secara lalai** (`enabled` mesti `true` dalam dokumen untuk dihidupkan).
4. **Tiada rahsia** (token, cookie, session ID, kata laluan keystore, `google-services.json`) dalam kod, Firestore atau Git. Jangan log header/body.
5. Jangan panggil endpoint sebenar semasa pembangunan/ujian. Ujian guna data rekaan (`example.invalid`).
6. Jangan padam/tulis semula kod sedia ada tanpa sebab; ubah secara berperingkat.

## Struktur
- `app/src/main/java/my/belalang/booking/data` - model, `ApiClient`, `ConfigRepository` (Firestore), `JobParser`, `JsonPath`
- `.../domain` - `JobFilterEngine` (carian, filter, sorting)
- `.../ui` - `BookingViewModel`, `BookingApp` (Compose)
- `app/src/test` - ujian unit (JVM)
- `docs/FIRESTORE.md` - skema dokumen Firestore

## Build
`gradle :app:testDebugUnitTest :app:assembleDebug` (CI: `.github/workflows/build.yml`).
Signing release guna env `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
