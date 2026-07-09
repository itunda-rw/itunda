const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');
const path = require('path');

// Mini-apps live outside host-app/node_modules (npm workspace symlinks), and
// import shared code from the sibling `packages/*` workspaces — Metro needs
// to watch those real source directories, not just host-app's own folder,
// or edits to a mini-app's page won't trigger a rebuild.
const workspaceRoot = path.resolve(__dirname, '..');

/** @type {import('metro-config').MetroConfig} */
const config = {
  watchFolders: [workspaceRoot],
  resolver: {
    nodeModulesPaths: [
      path.resolve(__dirname, 'node_modules'),
      path.resolve(workspaceRoot, 'node_modules'),
    ],
  },
};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);
