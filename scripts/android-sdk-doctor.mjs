#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';

const projects = ['thinklet-android', 'field-android'];
const envSdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || '';
const commonSdkPaths = [
  envSdk,
  join(homedir(), 'Library', 'Android', 'sdk'),
  join(homedir(), 'Android', 'Sdk'),
  '/opt/android-sdk',
  '/usr/local/share/android-sdk',
].filter(Boolean);

let hasProblem = false;

for (const project of projects) {
  const localPropertiesPath = join(project, 'local.properties');
  const configured = readSdkDir(localPropertiesPath);
  console.log(`\n[${project}]`);
  if (!configured) {
    hasProblem = true;
    console.log(`- ${localPropertiesPath}: sdk.dir is missing`);
  } else {
    console.log(`- sdk.dir=${configured}`);
    reportSdkPath(configured);
  }
}

console.log('\n[common candidates]');
for (const candidate of commonSdkPaths) {
  reportSdkPath(candidate);
}

if (hasProblem) {
  process.exitCode = 1;
}

function readSdkDir(path) {
  if (!existsSync(path)) {
    return null;
  }
  const content = readFileSync(path, 'utf8');
  const match = content.match(/^sdk\.dir=(.+)$/m);
  return match?.[1]?.trim() || null;
}

function reportSdkPath(path) {
  const platforms = join(path, 'platforms');
  const platformTools = join(path, 'platform-tools');
  const ok = existsSync(platforms) && existsSync(platformTools);
  if (!ok) {
    hasProblem = true;
  }
  console.log(`- ${path}: ${ok ? 'OK' : 'missing platforms or platform-tools'}`);
}
