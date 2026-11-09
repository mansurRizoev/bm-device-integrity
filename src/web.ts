import { WebPlugin } from '@capacitor/core';

import type { BmDeviceIntegrityPluginPlugin } from './definitions';

export class BmDeviceIntegrityPluginWeb extends WebPlugin implements BmDeviceIntegrityPluginPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
