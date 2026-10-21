import { join } from 'node:path';
import { createPairLink } from './server.js';

const port = Number(process.env.PORT ?? 3000);
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('PORT must be between 1 and 65535');
const apiKey = (process.env.PAIRLINK_API_KEY ?? '').trim();
if (!apiKey) throw new Error('PAIRLINK_API_KEY is required');
const app = createPairLink({ databasePath: join(process.env.DATA_DIR ?? './data', 'pairlink.sqlite'), apiKey });
await app.listen(port);
console.log(`PairLink listening on port ${port}`);
for (const signal of ['SIGTERM', 'SIGINT']) process.once(signal, async () => {
  const deadline = setTimeout(() => process.exit(1), 8000);
  deadline.unref();
  await app.close();
  clearTimeout(deadline);
  process.exit(0);
});
