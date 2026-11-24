import { WebPlugin } from '@capacitor/core';

import type {
  AttestKeyOptions,
  AttestKeyResult,
  BmDeviceIntegrityPluginPlugin,
  ClassicIntegrityOptions,
  GenerateAssertionOptions,
  GenerateAssertionResult,
  GenerateKeyResult,
  IntegrityTokenResult,
  IsSupportedResult,
  PrepareStandardOptions,
  StandardIntegrityOptions,
} from './definitions';

export class BmDeviceIntegrityPluginWeb extends WebPlugin implements BmDeviceIntegrityPluginPlugin {
  async isSupported(): Promise<IsSupportedResult> {
    return { supported: false, platform: 'web', reason: 'Device integrity is not available on the web.' };
  }

  async requestIntegrityToken(_options: ClassicIntegrityOptions): Promise<IntegrityTokenResult> {
    throw this.unavailable('Play Integrity is only available on Android.');
  }

  async prepareStandardIntegrity(_options: PrepareStandardOptions): Promise<void> {
    throw this.unavailable('Play Integrity is only available on Android.');
  }

  async requestStandardIntegrityToken(_options: StandardIntegrityOptions): Promise<IntegrityTokenResult> {
    throw this.unavailable('Play Integrity is only available on Android.');
  }

  async generateKey(): Promise<GenerateKeyResult> {
    throw this.unavailable('App Attest is only available on iOS.');
  }

  async attestKey(_options: AttestKeyOptions): Promise<AttestKeyResult> {
    throw this.unavailable('App Attest is only available on iOS.');
  }

  async generateAssertion(_options: GenerateAssertionOptions): Promise<GenerateAssertionResult> {
    throw this.unavailable('App Attest is only available on iOS.');
  }
}
