# Finvu × Aadhaar App — Enrolment Redirect Demo (Android)

A minimal, buildable Android demo of the **second-factor enrolment** flow we designed:
an FIU/parent app hosts an **embedded WebView** that renders Finvu's enrolment journey, and at
the right moment performs an **app-to-app redirect to the Aadhaar App** for offline face
verification, then receives a (mock) Verifiable Credential back.

> The redirection to the Aadhaar App is real (a genuine `Intent`). Because the new Aadhaar App's
> package name + OpenID4VP scheme aren't publicly confirmed, the app tries real candidates first
> and **falls back to a built-in simulator** so the round trip always completes on any device.

---

## What it demonstrates

1. **Embedded WebView as a launcher, not an executor.** The WebView renders the UI and fires the
   deep link; the camera capture / liveness / face match happen *inside the Aadhaar App's native
   process*, never in the WebView. This is the core architectural point — a WebView can't capture a
   face, but it can navigate to a deep link and let the OS hand off.
2. **The OpenID4VP round trip** — request generation (nonce, state, minimal Presentation
   Definition with `limit_disclosure: required`), redirect, signed-VP return, validation, and
   binding — surfaced live in an on-screen trace panel.
3. **Data minimisation** — only a masked reference + disclosed fields are "bound" to the AA handle;
   no Aadhaar number, no photo, no biometric template.

## Run it

1. Open the `FinvuAadhaarDemo` folder in **Android Studio** (Giraffe or newer).
2. Let Gradle sync (AGP 8.5.x / Kotlin 1.9.x / compileSdk 34 — adjust if your IDE prompts).
3. Run on an emulator or device (minSdk 24).
4. Tap **Verify with Aadhaar App** → the app attempts the real redirect; with no Aadhaar App
   installed it opens the **simulator** → tap **Simulate face scan & consent** → you're returned to
   Finvu's WebView with the credential, which you then **bind**.

## Project structure

```
FinvuAadhaarDemo/
├── settings.gradle.kts, build.gradle.kts, gradle.properties
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml          ← launcher + finvu://callback deep link + <queries>
        ├── assets/enrolment.html        ← Finvu enrolment UI + OpenID4VP trace (the WebView page)
        ├── java/com/finvu/aadhaardemo/
        │   ├── MainActivity.kt          ← WebView host + the app-to-app REDIRECTION + callback
        │   └── MockAadhaarActivity.kt   ← stand-in for UIDAI's Aadhaar App
        └── res/values/                  ← theme + strings
```

## The flow, mapped to code

| Step | Where | What happens |
|---|---|---|
| Build OpenID4VP request | `enrolment.html › startAadhaar()` | nonce/state + minimal Presentation Definition |
| **Redirect to Aadhaar App** | `MainActivity.launchAadhaar()` | real `Intent` to candidate packages / App Link → simulator fallback |
| Consent + on-device face match | `MockAadhaarActivity` (sim) / real Aadhaar App | returns a signed VP via `finvu://callback` |
| Receive + validate | `MainActivity.handleCallback()` → `onAadhaarResult()` | feeds VP into the WebView (mock validation) |
| Bind references | `enrolment.html › bind()` | stores masked ref + fields against the AA handle |

## ⚠️ Placeholders to replace before anything real

In `MainActivity.kt`:
- `AADHAAR_PACKAGE_CANDIDATES` — the real new-Aadhaar-App package name.
- `AADHAAR_APP_LINK` / `AADHAAR_CUSTOM_SCHEME` — the real OpenID4VP deep link / scheme.

Confirm all of these against **https://docs.uidai.gov.in** (Aadhaar App documentation / Wallet Flow
Specification), and the exact credential format (SD-JWT vs ISO mDOC), query language (Presentation
Exchange vs DCQL), and response mode (`direct_post` vs redirect).

## What is deliberately NOT production-grade

- **Validation is mocked and client-side.** In production the UIDAI signature, nonce, expiry,
  holder-binding and match result MUST be verified on **Finvu's server**, never in the app/WebView.
- The **VC is unsigned mock JSON.** A real presentation carries a UIDAI-signed SD-JWT / mDOC.
- No OVSE registration, no real consent-artefact persistence, no retention controls — those are
  the compliance layer the design report calls for.
- The simulator exists only so the round trip runs without the real Aadhaar App.
