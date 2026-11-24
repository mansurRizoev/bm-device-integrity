# bm-device-integrity

Capacitor plugin for Google Play Integrity and Apple App Attest verification on Android and iOS.

## Install

To use npm

```bash
npm install bm-device-integrity
````

To use yarn

```bash
yarn add bm-device-integrity
```

Sync native files

```bash
npx cap sync
```

## Платформенные требования

**Android (Play Integrity)**
- Приложение опубликовано в Google Play (хотя бы internal testing), в Play Console включён Play Integrity API и привязан Google Cloud-проект.
- Для standard-запросов нужен `cloudProjectNumber`.
- `nonce` (classic): base64 URL-safe, без padding, 16–500 байт, содержит хэш запроса.
- `requestHash` (standard): до 500 байт; рекомендуется `base64url(SHA-256(clientData))`.

**iOS (App Attest)**
- Entitlement `com.apple.developer.devicecheck.appattest-environment` = `development` / `production`.
- Не работает на симуляторе — проверяйте `isSupported()`.
- Нативный код считает `SHA256(UTF-8(challenge | clientData))`; backend обязан посчитать тот же хэш.

## Обязательная проверка на backend

Клиентский результат ничего не доказывает сам по себе.

| | Android (Google) | iOS |
|---|---|---|
| Что отдаёт плагин | зашифрованный токен | attestation (один раз на ключ) + assertions |
| Backend | `decodeIntegrityToken` (Google API); сверить `packageName`, `requestHash`, `appRecognitionVerdict`, `deviceRecognitionVerdict`, время | проверить цепочку до Apple App Attest Root CA, nonce, `rpIdHash` (App ID), `counter`, публичный ключ; по assertion — подпись и растущий `counter` |
| Привязка к запросу | `requestHash == base64url(SHA-256(clientData))` | подпись assertion над `SHA-256(clientData)` |
| Защита от replay | одноразовый challenge внутри `clientData` | одноразовый challenge внутри `clientData` + монотонный `counter` |

### Чек-лист backend

**Challenge**
- [ ] `challenge` — ≥ 32 случайных байта из CSPRNG, TTL ≈ 60 с.
- [ ] При выдаче сохраняется `{ challengeId, challenge, sessionId/userId, action, expiresAt, used: false }`. Выдача — только аутентифицированной сессии (кроме `login`/`register`) и с rate limit на сессию/IP.
- [ ] Погашение атомарное и в одной транзакции с самой операцией, например `UPDATE … SET used = true WHERE id = ? AND used = false AND expires_at > now() AND session_id = ? AND action = ? RETURNING challenge`. Нет строки — `403`.

**`clientData`**
- [ ] Хэш считается от **полученной строки** `integrity.clientData` как есть, без повторной сериализации.
- [ ] `JSON.parse(clientData)`: `challenge` совпадает с погашенным challenge, `action` — с эндпоинтом и с `action` challenge-а.
- [ ] Данные операции берутся **только из `clientData.payload`**. Если API принимает и обычное тело, оно должно быть строго равно `payload`, иначе `403`.

**Провайдер и политика (защита от downgrade)**
- [ ] На защищённых эндпоинтах доказательство **обязательно**: нет `integrity` или проверка не прошла — `403`. Никаких «мягких» режимов в проде.
- [ ] Поле `provider` от клиента не используется для решения. Провайдер определяется тем, что реально прошло проверку: токен расшифровался Google для вашего `packageName`, assertion подписан зарегистрированным `keyId`.
- [ ] Минимальные вердикты: Google — `appRecognitionVerdict == PLAY_RECOGNIZED` и `MEETS_DEVICE_INTEGRITY` в `deviceRecognitionVerdict`; Apple — ключ зарегистрирован за этим пользователем, `counter` растёт.
- [ ] Dev-обход — только флагом окружения dev-backend-а (например, `INTEGRITY_ENFORCE=false`), который физически отсутствует в прод-конфиге. Никогда не включайте обход по заголовку, user-agent или значению из запроса.

**App Attest**
- [ ] `register`: challenge погашен (`action = app-attest-register`), attestation проверен, `keyId` и публичный ключ сохранены за пользователем. Повторная регистрация того же `keyId` — `409`.
- [ ] Неизвестный или отозванный `keyId` — ответ с отдельным кодом (например, `INTEGRITY_KEY_UNKNOWN`), по которому клиент вызывает `resetAppAttestKey()`.

**Остаточный риск.** Ни один из механизмов не защищает от relay-атак, когда настоящее устройство под управлением атакующего проксирует доказательства. Привязка к сессии и действию, короткий TTL и rate limit делают такую атаку дорогой, но не невозможной. Комбинируйте с антифрод-правилами.

## Интеграция в приложение (Ionic + Angular / React / Vue)

Плагин не зависит от фреймворка: в любом Ionic/Capacitor-приложении сценарий один и тот же.

1. Определить провайдера: `isSupported()` → Google Play Integrity или Apple App Attest.
2. Перед защищённым действием запросить у backend **одноразовый challenge для этого действия** (сервер привязывает его к сессии).
3. Собрать `clientData = JSON.stringify({ action, challenge, payload })`, где `payload` — данные операции.
4. Получить от плагина доказательство, **криптографически привязанное к `clientData`**:
   - Google — `requestHash = base64url(SHA-256(clientData))`;
   - Apple — assertion над `SHA-256(clientData)` (хэш считает нативный код).
5. Отправить на backend `{ integrity: proof }`. Данные операции backend берёт **из `clientData.payload`**, а не из отдельных полей запроса. Решение о доверии принимает **только backend** (см. [чек-лист](#чек-лист-backend)).

Так подменить тело запроса, сохранив валидное доказательство, нельзя ни на одной платформе.

Ниже — общий клиент на TypeScript (п. 1) и тонкие обёртки для Angular (п. 2), React (п. 3) и Vue (п. 4).

### Установка

```bash
npm install bm-device-integrity @capacitor/preferences
npx cap sync
```

`@capacitor/preferences` нужен только для хранения `keyId` App Attest. `keyId` не секрет (сам ключ лежит в Secure Enclave), поэтому подойдёт любое хранилище.

Нативную настройку (Play Console, AppGallery Connect, entitlement App Attest) см. в разделе [Платформенные требования](#платформенные-требования).

### Контракт с backend (пример)

Эндпоинты условные, назовите их как удобно:

| Метод | Назначение |
|---|---|
| `POST /integrity/challenge` `{ action }` → `{ challengeId, challenge }` | одноразовый challenge, привязанный к сессии и действию, TTL ≈ 60 с |
| `POST /integrity/app-attest/register` `{ challengeId, keyId, attestation }` | iOS: однократная регистрация ключа App Attest |
| защищённый запрос `{ integrity: IntegrityProof }` | backend проверяет доказательство и берёт данные из `integrity.clientData` |

### 1. Общий клиент (без привязки к фреймворку)

`src/app/integrity/device-integrity.client.ts` (путь любой):

```ts
import { Preferences } from '@capacitor/preferences';
import { BmDeviceIntegrityPlugin } from 'bm-device-integrity';

export type IntegrityProvider = 'play' | 'appattest';

export interface IntegrityChallenge {
  challengeId: string;
  challenge: string;
}

/** Отправляется на backend вместо (или вместе с) телом защищённого запроса. */
export interface IntegrityProof {
  /** Подсказка для логов. Backend НЕ доверяет этому полю — провайдер определяется проверкой подписи. */
  provider: IntegrityProvider;
  challengeId: string;
  /** `JSON.stringify({ action, challenge, payload })` — ровно та строка, к которой привязано доказательство. */
  clientData: string;
  /** Google Play Integrity: `requestHash = base64url(SHA-256(clientData))`. */
  token?: string;
  /** Apple App Attest: assertion над `SHA-256(clientData)`. */
  keyId?: string;
  assertion?: string;
}

export interface IntegrityBackend {
  /** Сервер выдаёт challenge для конкретного действия и привязывает его к текущей сессии. */
  getChallenge(action: string): Promise<IntegrityChallenge>;
  registerAppAttestKey(body: { challengeId: string; keyId: string; attestation: string }): Promise<void>;
}

export interface DeviceIntegrityConfig {
  /** Номер Google Cloud-проекта (Play Console → App integrity). */
  cloudProjectNumber: number;
}

const KEY_ID = 'bm.appattest.keyId';
const KEY_ATTESTED = 'bm.appattest.attested';
const ATTEST_ACTION = 'app-attest-register';

/** base64url(SHA-256(UTF-8(text))) без padding — 43 символа. */
async function sha256Base64Url(text: string): Promise<string> {
  const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text)));
  let binary = '';
  digest.forEach((b) => (binary += String.fromCharCode(b)));
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function errorCode(e: unknown): string | undefined {
  return typeof e === 'object' && e !== null && 'code' in e ? String((e as { code: unknown }).code) : undefined;
}

export class DeviceIntegrityClient {
  private provider?: IntegrityProvider | null;
  private playPrepared?: Promise<void>;
  private appAttestKey?: Promise<string>;

  constructor(
    private readonly backend: IntegrityBackend,
    private readonly config: DeviceIntegrityConfig,
  ) {}

  /** `null` — аттестация недоступна (web, `ionic serve`, симулятор, нет Google Play Services). */
  async getProvider(): Promise<IntegrityProvider | null> {
    if (this.provider !== undefined) return this.provider;
    const info = await BmDeviceIntegrityPlugin.isSupported();
    if (!info.supported) this.provider = null;
    else if (info.platform === 'ios') this.provider = 'appattest';
    else if (info.googlePlayServices) this.provider = 'play';
    else this.provider = null;
    return this.provider;
  }

  /** Вызывайте при старте приложения: прогрев Play Integrity / регистрация ключа App Attest. */
  async warmUp(): Promise<void> {
    const provider = await this.getProvider();
    if (provider === 'play') await this.preparePlay();
    if (provider === 'appattest') await this.withAppAttestKey(async () => undefined);
  }

  /**
   * Доказательство целостности для одного защищённого действия.
   * @param action  имя операции (`'transfer'`, `'login'` …) — сервер сверит его с выданным challenge.
   * @param payload данные операции; backend должен брать их из `clientData`, а не из отдельных полей.
   */
  async getProof(action: string, payload: unknown = null): Promise<IntegrityProof> {
    const provider = await this.getProvider();
    // Без доказательства запрос должен отклоняться backend-ом — не пытаемся «обойти» это на клиенте.
    if (!provider) throw new Error('Device integrity is not available on this device');

    if (provider === 'appattest') {
      return this.withAppAttestKey(async (keyId) => {
        const { challengeId, challenge } = await this.backend.getChallenge(action);
        const clientData = JSON.stringify({ action, challenge, payload });
        const { assertion } = await BmDeviceIntegrityPlugin.generateAssertion({ keyId, clientData });
        return { provider, challengeId, clientData, keyId, assertion };
      });
    }

    const { challengeId, challenge } = await this.backend.getChallenge(action);
    const clientData = JSON.stringify({ action, challenge, payload });
    const binding = await sha256Base64Url(clientData);

    await this.preparePlay();
    const { token } = await BmDeviceIntegrityPlugin.requestStandardIntegrityToken({ requestHash: binding });
    return { provider, challengeId, clientData, token };
  }

  /** Сброс ключа App Attest — например, если backend ответил, что ключ неизвестен или отозван. */
  async resetAppAttestKey(): Promise<void> {
    this.appAttestKey = undefined;
    await Preferences.remove({ key: KEY_ID });
    await Preferences.remove({ key: KEY_ATTESTED });
  }

  private preparePlay(): Promise<void> {
    this.playPrepared ??= BmDeviceIntegrityPlugin.prepareStandardIntegrity({
      cloudProjectNumber: this.config.cloudProjectNumber,
    }).catch((e) => {
      this.playPrepared = undefined; // повторим при следующем вызове
      throw e;
    });
    return this.playPrepared;
  }

  /**
   * Выполняет `fn` с рабочим ключом App Attest. Если ключ оказался непригоден
   * (`APP_ATTEST_INVALID_KEY`: уже аттестован, но регистрация на backend не дошла; восстановлен
   * из бэкапа на другое устройство; удалён системой) — сбрасывает его и повторяет ОДИН раз с новым ключом.
   */
  private async withAppAttestKey<T>(fn: (keyId: string) => Promise<T>): Promise<T> {
    try {
      return await fn(await this.getAppAttestKey());
    } catch (e) {
      if (errorCode(e) !== 'APP_ATTEST_INVALID_KEY') throw e;
      await this.resetAppAttestKey();
      return fn(await this.getAppAttestKey());
    }
  }

  /** Один общий промис: параллельные вызовы не аттестуют ключ дважды. */
  private getAppAttestKey(): Promise<string> {
    this.appAttestKey ??= this.ensureAppAttestKey().catch((e) => {
      this.appAttestKey = undefined;
      throw e;
    });
    return this.appAttestKey;
  }

  /** Ключ создаётся и аттестуется один раз; дальше используется только для assertions. */
  private async ensureAppAttestKey(): Promise<string> {
    let { value: keyId } = await Preferences.get({ key: KEY_ID });
    const { value: attested } = await Preferences.get({ key: KEY_ATTESTED });
    if (keyId && attested === '1') return keyId;

    if (!keyId) {
      keyId = (await BmDeviceIntegrityPlugin.generateKey()).keyId;
      await Preferences.set({ key: KEY_ID, value: keyId });
    }
    const { challengeId, challenge } = await this.backend.getChallenge(ATTEST_ACTION);
    // Повторный attestKey для того же ключа вернёт APP_ATTEST_INVALID_KEY → withAppAttestKey создаст новый ключ.
    const { attestation } = await BmDeviceIntegrityPlugin.attestKey({ keyId, challenge });
    await this.backend.registerAppAttestKey({ challengeId, keyId, attestation });
    await Preferences.set({ key: KEY_ATTESTED, value: '1' });
    return keyId;
  }
}
```

> `crypto.subtle` доступен в WebView Capacitor (`https://localhost` на Android, `capacitor://localhost` на iOS — это secure context).

### 2. Ionic + Angular

**Сервис** — `src/app/integrity/device-integrity.service.ts`:

```ts
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';
import { DeviceIntegrityClient, IntegrityChallenge, IntegrityProof } from './device-integrity.client';

@Injectable({ providedIn: 'root' })
export class DeviceIntegrityService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  private readonly client = new DeviceIntegrityClient(
    {
      getChallenge: (action) =>
        firstValueFrom(this.http.post<IntegrityChallenge>(`${this.api}/integrity/challenge`, { action })),
      registerAppAttestKey: (body) =>
        firstValueFrom(this.http.post<void>(`${this.api}/integrity/app-attest/register`, body)),
    },
    { cloudProjectNumber: environment.cloudProjectNumber },
  );

  warmUp(): Promise<void> {
    return this.client.warmUp();
  }

  getProof(action: string, payload?: unknown): Promise<IntegrityProof> {
    return this.client.getProof(action, payload);
  }

  resetAppAttestKey(): Promise<void> {
    return this.client.resetAppAttestKey();
  }
}
```

**Прогрев при старте** — `src/main.ts` (standalone, Angular 19+):

```ts
import { provideAppInitializer, inject } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideIonicAngular } from '@ionic/angular/standalone';

import { AppComponent } from './app/app.component';
import { DeviceIntegrityService } from './app/integrity/device-integrity.service';

bootstrapApplication(AppComponent, {
  providers: [
    provideIonicAngular(),
    provideHttpClient(),
    provideAppInitializer(() => {
      // не блокируем старт и не роняем приложение, если аттестация недоступна
      inject(DeviceIntegrityService).warmUp().catch((e) => console.warn('[integrity] warm-up failed', e));
    }),
  ],
});
```

> В Angular ниже 19 вместо `provideAppInitializer` используйте `APP_INITIALIZER` с `useFactory`, а в NgModule-проекте — тот же провайдер в `AppModule`.

**Использование на странице:**

```ts
import { Component, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { IonButton, IonContent } from '@ionic/angular/standalone';
import { firstValueFrom } from 'rxjs';

import { DeviceIntegrityService } from '../integrity/device-integrity.service';

@Component({
  selector: 'app-transfer',
  standalone: true,
  imports: [IonContent, IonButton],
  template: `<ion-content><ion-button (click)="transfer()">Перевести</ion-button></ion-content>`,
})
export class TransferPage {
  private readonly http = inject(HttpClient);
  private readonly integrity = inject(DeviceIntegrityService);

  async transfer() {
    const integrity = await this.integrity.getProof('transfer', { to: '992900000000', amount: 100 });
    // Данные операции едут внутри integrity.clientData — backend берёт их оттуда.
    await firstValueFrom(this.http.post('/api/transfer', { integrity }));
  }
}
```

Доказательство можно добавлять и через `HttpInterceptorFn` (`from(this.integrity.getProof(action, req.body))` + `switchMap`), но только для явного списка URL. Исключите из перехвата сами `/integrity/*`, иначе получится рекурсия.

### 3. Ionic + React

**Хук** — `src/integrity/useDeviceIntegrity.tsx`:

```tsx
import { createContext, useContext, useEffect, useMemo, type PropsWithChildren } from 'react';

import { DeviceIntegrityClient } from './device-integrity.client';

const API = import.meta.env.VITE_API_URL;

async function postJson<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${API}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include', // challenge привязан к сессии
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`${path}: HTTP ${res.status}`);
  return res.status === 204 ? (undefined as T) : res.json();
}

const IntegrityContext = createContext<DeviceIntegrityClient | null>(null);

export function DeviceIntegrityProvider({ children }: PropsWithChildren) {
  const client = useMemo(
    () =>
      new DeviceIntegrityClient(
        {
          getChallenge: (action) => postJson('/integrity/challenge', { action }),
          registerAppAttestKey: (body) => postJson('/integrity/app-attest/register', body),
        },
        { cloudProjectNumber: Number(import.meta.env.VITE_CLOUD_PROJECT_NUMBER) },
      ),
    [],
  );

  useEffect(() => {
    client.warmUp().catch((e) => console.warn('[integrity] warm-up failed', e));
  }, [client]);

  return <IntegrityContext.Provider value={client}>{children}</IntegrityContext.Provider>;
}

export function useDeviceIntegrity(): DeviceIntegrityClient {
  const client = useContext(IntegrityContext);
  if (!client) throw new Error('useDeviceIntegrity must be used inside <DeviceIntegrityProvider>');
  return client;
}
```

**Подключение** — `src/App.tsx`:

```tsx
import { IonApp, setupIonicReact } from '@ionic/react';

import { DeviceIntegrityProvider } from './integrity/useDeviceIntegrity';

setupIonicReact();

export default function App() {
  return (
    <IonApp>
      <DeviceIntegrityProvider>{/* IonReactRouter, страницы и т.д. */}</DeviceIntegrityProvider>
    </IonApp>
  );
}
```

**Использование:**

```tsx
import { IonButton, IonContent, IonPage } from '@ionic/react';
import { useState } from 'react';

import { useDeviceIntegrity } from '../integrity/useDeviceIntegrity';

export default function TransferPage() {
  const integrity = useDeviceIntegrity();
  const [busy, setBusy] = useState(false);

  const transfer = async () => {
    setBusy(true);
    try {
      const proof = await integrity.getProof('transfer', { to: '992900000000', amount: 100 });
      await fetch('/api/transfer', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ integrity: proof }),
      });
    } finally {
      setBusy(false);
    }
  };

  return (
    <IonPage>
      <IonContent>
        <IonButton disabled={busy} onClick={transfer}>
          Перевести
        </IonButton>
      </IonContent>
    </IonPage>
  );
}
```

> React StrictMode в dev вызывает `useEffect` дважды. Это безопасно: прогрев Play и создание ключа App Attest кэшируются в одном промисе.

### 4. Ionic + Vue

`src/integrity/useDeviceIntegrity.ts`:

```ts
import { DeviceIntegrityClient } from './device-integrity.client';

const API = import.meta.env.VITE_API_URL;

async function post(path: string, body: unknown) {
  const r = await fetch(`${API}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(body),
  });
  if (!r.ok) throw new Error(`${path}: HTTP ${r.status}`);
  return r.status === 204 ? undefined : r.json();
}

// один экземпляр на приложение
const client = new DeviceIntegrityClient(
  {
    getChallenge: (action) => post('/integrity/challenge', { action }),
    registerAppAttestKey: (body) => post('/integrity/app-attest/register', body),
  },
  { cloudProjectNumber: Number(import.meta.env.VITE_CLOUD_PROJECT_NUMBER) },
);

export const useDeviceIntegrity = () => client;
```

В `src/main.ts` после `app.mount('#app')`:

```ts
useDeviceIntegrity().warmUp().catch((e) => console.warn('[integrity] warm-up failed', e));
```

В компоненте: `const proof = await useDeviceIntegrity().getProof('transfer', body);`.

### Другие стеки

- **Capacitor без Ionic / Vanilla / Svelte / Solid** — используйте `DeviceIntegrityClient` из п. 1 напрямую.
- **Ionic Angular на NgModule** — тот же сервис из п. 2 (`providedIn: 'root'`), прогрев через `APP_INITIALIZER` в `AppModule`.

### Типичные ошибки

| Симптом | Причина / решение |
|---|---|
| `getProvider()` → `null` в `ionic serve` | Браузер: плагин работает только на устройстве. Тестируйте на эмуляторе/устройстве. Обход проверки — только флагом окружения **dev-backend-а**, никогда не по признаку из запроса (см. [чек-лист](#чек-лист-backend)). |
| iOS: `supported: false` | Симулятор или нет entitlement App Attest. Проверяйте на реальном устройстве. |
| `INVALID_ARGUMENT` | Неверные параметры: `cloudProjectNumber` не положительное целое, `requestHash` > 500 байт, nonce вне допустимой длины/формата. |
| Android: `NOT_PREPARED` | Не вызван `prepareStandardIntegrity` — клиент из п. 1 делает это сам. |
| Android: `INTEGRITY_ERROR_<n>` | `IntegrityErrorCode` Google: приложение не из Play, не привязан Cloud-проект, нет сети и т.п. |
| iOS: `APP_ATTEST_INVALID_KEY` | Ключ непригоден. Клиент из п. 1 сам сбрасывает его и создаёт новый (один повтор). |
| iOS: backend не знает `keyId` | Вызовите `resetAppAttestKey()` — при следующем `getProof()` ключ создастся и зарегистрируется заново. |


## API


<docgen-index>

* [`isSupported()`](#issupported)
* [`requestIntegrityToken(...)`](#requestintegritytoken)
* [`prepareStandardIntegrity(...)`](#preparestandardintegrity)
* [`requestStandardIntegrityToken(...)`](#requeststandardintegritytoken)
* [`generateKey()`](#generatekey)
* [`attestKey(...)`](#attestkey)
* [`generateAssertion(...)`](#generateassertion)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

Плагин содержит два РАЗНЫХ механизма. Они не взаимозаменяемы:

- **Android** — Google Play Integrity: приложение получает подписанный токен
  целостности и отправляет его на backend, который расшифровывает/проверяет
  его через Google (`decodeIntegrityToken`).
- **iOS** — Apple App Attest: приложение создаёт ключ в Secure Enclave,
  один раз «аттестует» его (attestation), а затем подписывает запросы
  (assertions). Backend проверяет цепочку сертификатов, nonce, счётчик и подпись.

Клиент НИКОГДА не принимает решение о доверии сам — всё проверяется на backend.

### isSupported()

```typescript
isSupported() => Promise<IsSupportedResult>
```

Проверяет доступность Play Integrity (Android) / App Attest (iOS).

**Returns:** <code>Promise&lt;<a href="#issupportedresult">IsSupportedResult</a>&gt;</code>

--------------------


### requestIntegrityToken(...)

```typescript
requestIntegrityToken(options: ClassicIntegrityOptions) => Promise<IntegrityTokenResult>
```

Android, **classic** request. Один токен на один запрос.
Подходит для редких, самых важных действий.

| Param         | Type                                                                        |
| ------------- | --------------------------------------------------------------------------- |
| **`options`** | <code><a href="#classicintegrityoptions">ClassicIntegrityOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#integritytokenresult">IntegrityTokenResult</a>&gt;</code>

--------------------


### prepareStandardIntegrity(...)

```typescript
prepareStandardIntegrity(options: PrepareStandardOptions) => Promise<void>
```

Android, **standard** request, шаг 1: «прогрев» провайдера.
Вызывайте заранее (например, при старте приложения); занимает секунды.

| Param         | Type                                                                      |
| ------------- | ------------------------------------------------------------------------- |
| **`options`** | <code><a href="#preparestandardoptions">PrepareStandardOptions</a></code> |

--------------------


### requestStandardIntegrityToken(...)

```typescript
requestStandardIntegrityToken(options: StandardIntegrityOptions) => Promise<IntegrityTokenResult>
```

Android, **standard** request, шаг 2: токен, привязанный к `requestHash`.
Требует предварительного {@link prepareStandardIntegrity}.

| Param         | Type                                                                          |
| ------------- | ----------------------------------------------------------------------------- |
| **`options`** | <code><a href="#standardintegrityoptions">StandardIntegrityOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#integritytokenresult">IntegrityTokenResult</a>&gt;</code>

--------------------


### generateKey()

```typescript
generateKey() => Promise<GenerateKeyResult>
```

Создаёт ключ App Attest. Сохраните `keyId` (например, в Keychain)
и привяжите его к пользователю на backend.

**Returns:** <code>Promise&lt;<a href="#generatekeyresult">GenerateKeyResult</a>&gt;</code>

--------------------


### attestKey(...)

```typescript
attestKey(options: AttestKeyOptions) => Promise<AttestKeyResult>
```

Аттестует ключ. Выполняется **один раз на ключ**.
`challenge` — одноразовая строка, выданная backend. Нативный код считает
`SHA256(UTF-8(challenge))` и передаёт хэш Apple.

| Param         | Type                                                          |
| ------------- | ------------------------------------------------------------- |
| **`options`** | <code><a href="#attestkeyoptions">AttestKeyOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#attestkeyresult">AttestKeyResult</a>&gt;</code>

--------------------


### generateAssertion(...)

```typescript
generateAssertion(options: GenerateAssertionOptions) => Promise<GenerateAssertionResult>
```

Подписывает данные запроса ключом, уже прошедшим attestation.
`clientData` — то, что backend должен получить вместе с assertion
(например, JSON тела запроса + одноразовый challenge). Нативный код
считает `SHA256(UTF-8(clientData))`; backend пересчитывает тот же хэш.

| Param         | Type                                                                          |
| ------------- | ----------------------------------------------------------------------------- |
| **`options`** | <code><a href="#generateassertionoptions">GenerateAssertionOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#generateassertionresult">GenerateAssertionResult</a>&gt;</code>

--------------------


### Interfaces


#### IsSupportedResult

| Prop                     | Type                                                            | Description                                                 |
| ------------------------ | --------------------------------------------------------------- | ----------------------------------------------------------- |
| **`supported`**          | <code>boolean</code>                                            | `true`, если на устройстве можно получить аттестацию.       |
| **`platform`**           | <code><a href="#integrityplatform">IntegrityPlatform</a></code> |                                                             |
| **`reason`**             | <code>string</code>                                             | Причина, если `supported === false`.                        |
| **`googlePlayServices`** | <code>boolean</code>                                            | Android: доступны ли Google Play Services (Play Integrity). |


#### IntegrityTokenResult

| Prop        | Type                | Description                                              |
| ----------- | ------------------- | -------------------------------------------------------- |
| **`token`** | <code>string</code> | Зашифрованный токен; расшифровывается ТОЛЬКО на backend. |


#### ClassicIntegrityOptions

| Prop                     | Type                | Description                                                                                                                      |
| ------------------------ | ------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| **`nonce`**              | <code>string</code> | Nonce от backend: base64 URL-safe, без переноса и padding-а, от 16 до 500 байт (требование Google). Должен включать хэш запроса. |
| **`cloudProjectNumber`** | <code>number</code> | Номер Cloud-проекта, привязанного к приложению (необязательно для classic).                                                      |


#### PrepareStandardOptions

| Prop                     | Type                | Description                                                           |
| ------------------------ | ------------------- | --------------------------------------------------------------------- |
| **`cloudProjectNumber`** | <code>number</code> | Номер Google Cloud-проекта, привязанного к приложению в Play Console. |


#### StandardIntegrityOptions

| Prop              | Type                | Description                                                                       |
| ----------------- | ------------------- | --------------------------------------------------------------------------------- |
| **`requestHash`** | <code>string</code> | Хэш запроса (до 500 байт), который backend сверит с `requestDetails.requestHash`. |


#### GenerateKeyResult

| Prop        | Type                |
| ----------- | ------------------- |
| **`keyId`** | <code>string</code> |


#### AttestKeyResult

| Prop              | Type                | Description                     |
| ----------------- | ------------------- | ------------------------------- |
| **`attestation`** | <code>string</code> | base64 CBOR attestation object. |
| **`keyId`**       | <code>string</code> |                                 |


#### AttestKeyOptions

| Prop            | Type                | Description                      |
| --------------- | ------------------- | -------------------------------- |
| **`keyId`**     | <code>string</code> |                                  |
| **`challenge`** | <code>string</code> | Одноразовый challenge с backend. |


#### GenerateAssertionResult

| Prop            | Type                | Description                   |
| --------------- | ------------------- | ----------------------------- |
| **`assertion`** | <code>string</code> | base64 CBOR assertion object. |


#### GenerateAssertionOptions

| Prop             | Type                | Description                                                  |
| ---------------- | ------------------- | ------------------------------------------------------------ |
| **`keyId`**      | <code>string</code> |                                                              |
| **`clientData`** | <code>string</code> | Данные запроса, которые подписываются (см. описание метода). |


### Type Aliases


#### IntegrityPlatform

<code>'android' | 'ios' | 'web'</code>

</docgen-api>
