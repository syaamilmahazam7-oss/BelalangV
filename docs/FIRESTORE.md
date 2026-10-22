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

Medan tambahan `step1_job_list`:

| Medan | Maksud |
|---|---|
| `listPath` | Laluan JSON ke array job, cth `a.b[0].c` (kosong = akar respons ialah array) |
| `fieldMap` | map kunci kad -> laluan dalam setiap item. Kunci: `id` (wajib), `jobType`, `title`, `pickup`, `dropoff`, `distanceKm`, `fare`, `time`, `serviceType` |
| `serviceTypeValues` | map `klia` / `normal` -> nilai untuk `{serviceType}` |
| `kliaKeywords` | list<string>: padanan pada title/dropoff/serviceType untuk tapisan KLIA di sisi app. Kosong = tiada tapisan |

Contoh bentuk `step1_job_list` (nilai rekaan):
```
url: "https://example.invalid/bookings?type={serviceType}"
method: "GET"
headers: { "Accept": "application/json" }
listPath: "data.items"
fieldMap: { id: "ref", pickup: "from", dropoff: "to", distanceKm: "dist", fare: "price", time: "at" }
serviceTypeValues: { klia: "…", normal: "…" }
kliaKeywords: ["KLIA"]
enabled: true
```
`step4_future`: letak `enabled: false` (atau biarkan tiada medan) sehingga sedia.

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
