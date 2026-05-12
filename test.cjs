const { execSync } = require('child_process');
try {
  console.log(execSync('ls -la $ANDROID_HOME/platforms', { encoding: 'utf8' }));
} catch (e) {
  console.log(e.message);
}
