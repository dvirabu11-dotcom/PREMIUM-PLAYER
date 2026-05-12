const { execSync } = require('child_process');
try {
  execSync('gradle assembleDebug', { stdio: 'inherit' });
} catch (e) {
  process.exit(1);
}
