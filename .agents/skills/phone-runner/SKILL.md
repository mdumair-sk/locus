---
name: phone-runner
description: >
  Manage deployment, on-device testing, logcat inspection, and native execution
  on the connected Snapdragon 8 Elite phone via ADB and Termux SSH tunnel.
---

# Phone Runner (Snapdragon 8 Elite)

Use this skill whenever verifying, deploying, or testing Locus on the connected Android phone.

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

- **Execute on Phone Oryon Cores**:
  `sh ./tools/phone.sh ssh "<command>"`
