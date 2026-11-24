export type IntegrityPlatform = 'android' | 'ios' | 'web';

export interface IsSupportedResult {
  /** `true`, если на устройстве можно получить аттестацию. */
  supported: boolean;
  platform: IntegrityPlatform;
  /** Причина, если `supported === false`. */
  reason?: string;
  /** Android: доступны ли Google Play Services (Play Integrity). */
  googlePlayServices?: boolean;
}

/**
 * Плагин содержит два РАЗНЫХ механизма. Они не взаимозаменяемы:
 *
 * - **Android** — Google Play Integrity: приложение получает подписанный токен
 *   целостности и отправляет его на backend, который расшифровывает/проверяет
 *   его через Google (`decodeIntegrityToken`).
 * - **iOS** — Apple App Attest: приложение создаёт ключ в Secure Enclave,
 *   один раз «аттестует» его (attestation), а затем подписывает запросы
 *   (assertions). Backend проверяет цепочку сертификатов, nonce, счётчик и подпись.
 *
 * Клиент НИКОГДА не принимает решение о доверии сам — всё проверяется на backend.
 */
export interface BmDeviceIntegrityPluginPlugin {
  /** Проверяет доступность Play Integrity (Android) / App Attest (iOS). */
  isSupported(): Promise<IsSupportedResult>;

  // ───────────────────────── Android: Play Integrity ─────────────────────────

  /**
   * Android, **classic** request. Один токен на один запрос.
   * Подходит для редких, самых важных действий.
   *
   * @platform android
   */
  requestIntegrityToken(options: ClassicIntegrityOptions): Promise<IntegrityTokenResult>;

  /**
   * Android, **standard** request, шаг 1: «прогрев» провайдера.
   * Вызывайте заранее (например, при старте приложения); занимает секунды.
   *
   * @platform android
   */
  prepareStandardIntegrity(options: PrepareStandardOptions): Promise<void>;

  /**
   * Android, **standard** request, шаг 2: токен, привязанный к `requestHash`.
   * Требует предварительного {@link prepareStandardIntegrity}.
   *
   * @platform android
   */
  requestStandardIntegrityToken(options: StandardIntegrityOptions): Promise<IntegrityTokenResult>;

  // ───────────────────────── iOS: App Attest ─────────────────────────

  /**
   * Создаёт ключ App Attest. Сохраните `keyId` (например, в Keychain)
   * и привяжите его к пользователю на backend.
   *
   * @platform ios
   */
  generateKey(): Promise<GenerateKeyResult>;

  /**
   * Аттестует ключ. Выполняется **один раз на ключ**.
   * `challenge` — одноразовая строка, выданная backend. Нативный код считает
   * `SHA256(UTF-8(challenge))` и передаёт хэш Apple.
   *
   * @platform ios
   */
  attestKey(options: AttestKeyOptions): Promise<AttestKeyResult>;

  /**
   * Подписывает данные запроса ключом, уже прошедшим attestation.
   * `clientData` — то, что backend должен получить вместе с assertion
   * (например, JSON тела запроса + одноразовый challenge). Нативный код
   * считает `SHA256(UTF-8(clientData))`; backend пересчитывает тот же хэш.
   *
   * @platform ios
   */
  generateAssertion(options: GenerateAssertionOptions): Promise<GenerateAssertionResult>;
}

/**
 * Коды ошибок (`error.code`), которые возвращает плагин.
 *
 * - `INVALID_ARGUMENT` — неверные параметры вызова.
 * - `NOT_PREPARED` — не вызван `prepareStandardIntegrity`.
 * - `INTEGRITY_ERROR_<n>` — `IntegrityErrorCode` Google Play Integrity.
 * - `APP_ATTEST_INVALID_KEY` — ключ App Attest непригоден: сбросьте `keyId` и создайте новый ключ.
 * - Остальные `APP_ATTEST_*` — прочие ошибки DeviceCheck.
 */
export type IntegrityErrorCode =
  | 'INVALID_ARGUMENT'
  | 'NOT_PREPARED'
  | 'APP_ATTEST_INVALID_KEY'
  | 'APP_ATTEST_UNSUPPORTED'
  | 'APP_ATTEST_INVALID_INPUT'
  | 'APP_ATTEST_SERVER_UNAVAILABLE'
  | 'APP_ATTEST_SYSTEM_FAILURE'
  | 'APP_ATTEST_ERROR'
  | `INTEGRITY_ERROR${string}`;

export interface ClassicIntegrityOptions {
  /**
   * Nonce от backend: base64 URL-safe, без переноса и padding-а,
   * от 16 до 500 байт (требование Google). Должен включать хэш запроса.
   */
  nonce: string;
  /** Номер Cloud-проекта, привязанного к приложению (необязательно для classic). */
  cloudProjectNumber?: number;
}

export interface PrepareStandardOptions {
  /** Номер Google Cloud-проекта, привязанного к приложению в Play Console. */
  cloudProjectNumber: number;
}

export interface StandardIntegrityOptions {
  /** Хэш запроса (до 500 байт), который backend сверит с `requestDetails.requestHash`. */
  requestHash: string;
}

export interface IntegrityTokenResult {
  /** Зашифрованный токен; расшифровывается ТОЛЬКО на backend. */
  token: string;
}

export interface GenerateKeyResult {
  keyId: string;
}

export interface AttestKeyOptions {
  keyId: string;
  /** Одноразовый challenge с backend. */
  challenge: string;
}

export interface AttestKeyResult {
  /** base64 CBOR attestation object. */
  attestation: string;
  keyId: string;
}

export interface GenerateAssertionOptions {
  keyId: string;
  /** Данные запроса, которые подписываются (см. описание метода). */
  clientData: string;
}

export interface GenerateAssertionResult {
  /** base64 CBOR assertion object. */
  assertion: string;
}
