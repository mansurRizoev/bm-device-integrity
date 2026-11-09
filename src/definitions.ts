export interface BmDeviceIntegrityPluginPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
