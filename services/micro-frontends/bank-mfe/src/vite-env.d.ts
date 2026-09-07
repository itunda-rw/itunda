/// <reference types="vite/client" />
declare module 'maps_mfe/MapView' {
  import type { ComponentType } from 'react';
  const MapView: ComponentType;
  export default MapView;
}
declare module 'maps_mfe/I18nProvider' {
  import type { ComponentType, PropsWithChildren } from 'react';
  const I18nProvider: ComponentType<PropsWithChildren>;
  export default I18nProvider;
}
