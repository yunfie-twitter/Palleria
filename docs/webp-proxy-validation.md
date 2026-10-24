# WebP proxy source host correction

The Palleria image proxy used `piximg.2wf6bfhvwk.workers.dev` as its nested source host. The deployed source is `pximg.2wf6bfhvwk.workers.dev`. Correcting the shared URL builder fixes requests from image display and prefetch callers that select the Palleria proxy; the outer WebP endpoint and URL encoding remain unchanged.

On 2026-10-24, HTTP GET checks with the application's image Referer and User-Agent headers produced:

| Request | Status | Content type | Content length | Body signature |
| --- | --- | --- | --- | --- |
| User-provided WebP URL for `88468474_p0_master1200.jpg` | 200 | image/webp | 39110 | RIFF / WEBP |
| Previous application URL, with encoded `piximg...` source | 404 | text/plain; charset=UTF-8 | not asserted | empty error body |
| Corrected application URL, with encoded `pximg...` source | 200 | image/webp | 39110 | RIFF / WEBP |

The successful responses had identical first 32 bytes. These checks establish the host typo and valid WebP response signature; they do not establish byte equality of the whole images or an on-device rendering result. No service, proxy setting, cache data or production infrastructure was changed.

The URL regression suite covers the corrected source host, passthrough of the provided already-converted URL, preservation of nested query parameters, other proxy hosts, unrelated URLs and normalization of the proxy setting. Live HTTP checks are kept outside unit tests so CI does not depend on the public image service.

Validation: the focused PixivImageProxyTest suite passed all 6 tests with zero failures/errors/skips. Kotlin compilation and main/test ktlint passed. The combined Gradle invocation completed successfully in 2m 31s. On-device rendering and a new APK release were not performed for this change.
