/// <reference types="vite/client" />

interface ZhixuBootApi {
  log: (line: string) => void;
  step: (text: string) => void;
}

interface Window {
  __zhixuBoot?: ZhixuBootApi;
}
