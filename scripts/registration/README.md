# Registration test utilities

These are optional local tools for registration concurrency tests. They are not application runtime code.

- `create-limited-pass.mjs`: creates a limited registration pass for testing.
- `gen-users-csv.mjs`: creates test accounts and a CSV file. Use `OUT=<path>` to choose where the generated file is saved.
- `concurrent-registration.mjs`: runs a small concurrent registration test.
- `burst-registration.mjs`: runs a high-concurrency registration test using a generated CSV file.

Run from the repository root so the workspace's Node runtime and local services are used. Generated CSV files and logs are ignored by Git.
