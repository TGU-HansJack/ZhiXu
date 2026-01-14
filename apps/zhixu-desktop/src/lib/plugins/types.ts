export type PluginAction = {
  id: string;
  label: string;
  icon?: string;
  place?: string;
  ringIndex?: number;
};

export type PluginManifest = {
  id: string;
  name: string;
  version: string;
  description?: string;
  entry?: string;
  files?: string[];
  actions?: PluginAction[];
};

export type PluginIndexItem = {
  id: string;
  name?: string;
  version?: string;
  description?: string;
  platforms?: Array<"desktop" | "android" | "web">;
};

export type PluginIndex = {
  version: number;
  plugins: PluginIndexItem[];
};

export type InstalledPlugin = {
  manifest: PluginManifest;
  enabled: boolean;
  configText: string | null;
  readmeText: string | null;
};

