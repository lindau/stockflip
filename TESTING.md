## Automated tests

- Run deterministic unit tests:

```bash
./gradlew testDebugUnitTest
```

These tests are network-free and include MockWebServer fixtures for Yahoo chart responses.

## Live smoke tests (manual)

Some tests are intentionally ignored because they call live external APIs and can be flaky due to network/rate limits.

- Run the live smoke tests by temporarily removing `@Ignore` from `app/src/test/java/com/stockflip/YahooFinanceServiceTest.kt`, or by copying the test methods into a separate local-only test class.

Recommended symbols:
- Swedish: `VOLV-B.ST`
- US: `AAPL`
- Crypto: `BTC-USD`
- Norway: `EQNR.OL`

