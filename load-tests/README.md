# Load & Health Tests

[k6](https://k6.io/) scripts for load-testing and health-checking the ResumeForge AI backend. These are dev/ops tools, not part of the application build.

## Prerequisites

Install k6: https://k6.io/docs/get-started/installation/

## Scripts

| Script                        | Purpose                                                                 |
|--------------------------------|--------------------------------------------------------------------------|
| `load-test.js`                | Basic ramp-up load test against `/api/health`                           |
| `health-check-load-test.js`   | Sustained high-throughput hammering of `/api/health` (up to 200 VUs)     |
| `stress-test.js`               | Extreme ramp to 300 concurrent users to find breaking points             |
| `db-connection-test.js`        | Stresses endpoints that hit the database, to validate connection pooling |
| `graceful-shutdown-test.js`    | Sustains load while you manually kill/restart the backend, to verify graceful shutdown behavior |

## Running

```bash
k6 run load-tests/load-test.js
```

By default these scripts target `http://localhost:8080`. To point at a different environment, edit the URL in the script or pass it via an environment variable if you've parameterized it, e.g.:

```bash
k6 run -e BASE_URL=https://api.resumeforgeai.site load-tests/load-test.js
```

⚠️ **Do not run `stress-test.js` or `graceful-shutdown-test.js` against production** unless you intend to actually stress or interrupt it. Use a staging environment.
