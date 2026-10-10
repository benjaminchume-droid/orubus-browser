# Third-party notices

## Bundled browser engine

Orubus currently depends on the Cefrium SDK, version 0.9.4, published as com.cefrium:cefrium-sdk:0.9.4. Cefrium is documented as LGPL-3.0-or-later. Cefrium embeds Chromium through the Chromium Embedded Framework (CEF); Chromium and CEF have their own BSD-style license terms and third-party notices. The full-codec SDK variant may also carry codec patent or licensing considerations.

Before redistributing stable APKs, obtain the exact license texts and notices for the pinned artifacts, include all required notices in the app distribution, document the source and version of the SDK, and satisfy applicable LGPL source/relinking obligations. Do not assume the top-level engine license covers every bundled dependency or codec.

Official project documentation:
- Cefrium: https://cefrium.com/
- Cefrium license and distribution notes: https://cefrium.com/about/
- Chromium source and license information: https://www.chromium.org/chromium-projects/

## Other planned components

Tor, WireGuard, and additional filter-list sources have not yet been integrated. Before adding them, record their exact versions, licenses, required notices, source-offer obligations where applicable, and any modifications. Do not ship third-party binaries until this review is complete.
