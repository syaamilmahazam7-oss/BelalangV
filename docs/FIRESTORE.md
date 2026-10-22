# Skema Firestore: koleksi `api_configs`

Empat dokumen: `step1_job_list`, `step2_job_details`, `step3_confirmation`, `step4_future`.
Semua nilai di bawah ialah **contoh bentuk** sahaja; isi dengan nilai sebenar melalui Firebase Console.
**Jangan letak token/cookie/session ID di sini.** Koleksi ini dibaca oleh app, jadi anggap ia tidak rahsia.

| Medan | Jenis | Nota |
|---|---|---|
| `url` | string | Boleh guna `{jobId}`, `{jobType}` (step2-4) dan `{serviceType}` (step1) |
| `method` | string | GET / POST / PUT / PATCH / DELETE (lalai GET) |
| `headers` | map<string,string> | Tanpa rahsia. Boleh guna placeholder di atas |
| `body` | string | Templat teks (cth JSON) dengan placeholder |
| `enabled` | bool | Lalai `true`; **lalai `false` untuk `step4_future`** |
| `timeoutMs` | number | 1000-120000, lalai 15000 |

## Aliran dan pembahagian medan

Step 1 (senarai) -> tapisan (jenis servis, tambang, masa) -> Step 2 (butiran, hanya untuk yang lulus) -> criteria (lokasi, km).
Pickup, dropoff dan km datang dari **Step 2**, bukan Step 1.

### `step1_job_list`
| Medan | Maksud |
|---|---|
| `listPath` | Laluan JSON ke array job (kosong = akar respons ialah array) |
| `fieldMap` | `id` (booking ID, **wajib**), `serviceType`, `fare`, `time` (pilihan: `jobType`, `title`) |
| `serviceTypeValues` | map `klia` / `normal` -> nilai untuk `{serviceType}` dalam URL/body (jika API terima parameter itu) |
| `kliaKeywords` | list<string>: padanan pada `serviceType` untuk mengenal servis KLIA di sisi app. Kosong = tiada tapisan servis |

### `step2_job_details`
| Medan | Maksud |
|---|---|
| `url` | Letak `{jobId}` di tempat booking ID diperlukan (URL, query atau `body`) |
| `detailPath` | Laluan ke objek butiran dalam respons (kosong = akar) |
| `fieldMap` | `pickup`, `dropoff`, `distanceKm` -> laluan dalam objek butiran |

### `step3_confirmation` dan `step4_future`
Belum ada API. Cipta dokumen dengan `enabled: false` dan `url` kosong. App akan menunjukkan "disabled".

Laluan JSON guna format `a.b[0].c`. Teks `distanceKm` dan `fare` boleh berformat `12.5 Km` / `RM80.00` (nombor pertama diambil).

## Log dalam app
Setiap pusingan dilog: HTTP kod Step 1 dan bilangan job; setiap job: lulus/gagal tapisan Step 1 beserta sebab; setiap panggilan Step 2: HTTP kod, pickup/dropoff/km dibaca dan keputusan criteria. Header, body request dan token **tidak** dilog.

## Peraturan keselamatan Firestore (cadangan)
Baca sahaja untuk klien, tulis hanya melalui Console/Admin:
```
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {
    match /api_configs/{doc} { allow read: if true; allow write: if false; }
  }
}
```
Pertimbangkan Firebase Auth/App Check jika konfigurasi tidak sepatutnya boleh dibaca umum.
