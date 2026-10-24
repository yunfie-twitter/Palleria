# Search input whitespace regression

On 2026-10-24, `:app:testDebugUnitTest --tests com.yunfie.illustia.platform.StealthMicroUxPhase1Test` passed **5 tests**, with zero failures, errors or skipped tests. This corrects the 13-test count mistakenly written in commit `05eb4eaa`; the current suite XML reports five tests.

The search-input test covers intermediate typing states, leading/trailing spaces, repeated separators, full-width spaces, and enabled/disabled sanitization. Existing URL/ID normalization assertions also pass. The fix removes per-keystroke trimming from editable search text, while leaving intent detection's parsing behavior intact.

This host test does not establish physical-device IME behavior or overall application performance.
