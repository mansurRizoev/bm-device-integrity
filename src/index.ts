import { registerPlugin } from '@capacitor/core';

import type { BmDeviceIntegrityPluginPlugin } from './definitions';

const BmDeviceIntegrityPlugin = registerPlugin<BmDeviceIntegrityPluginPlugin>('BmDeviceIntegrityPlugin', {
  web: () => import('./web').then((m) => new m.BmDeviceIntegrityPluginWeb()),
});

export * from './definitions';
export { BmDeviceIntegrityPlugin };
