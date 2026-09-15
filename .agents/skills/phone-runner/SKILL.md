---
name: phone-runner
description: >
  Manage deployment, on-device testing, logcat inspection, and native execution
  on the connected phone via ADB and Termux SSH tunnel.
---

# Phone Runner

Use this skill whenever verifying, deploying, or testing Locus on the connected phone.

## Commands

- **Check device & SSH node**:
  `sh ./tools/phone.sh status`

- **Build & Deploy App**:
  `sh ./tools/phone.sh deploy oss`
  `sh ./tools/phone.sh deploy full`

- **Run Connected Tests**:
  `sh ./tools/phone.sh test`

- **Inspect Logs**:
  `sh ./tools/phone.sh logcat`

- **Execute on Phone**:
  `sh ./tools/phone.sh ssh "<command>"`
