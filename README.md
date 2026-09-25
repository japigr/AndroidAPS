# AAPS + tuning data export (fork)

This is a fork of [nightscout/AndroidAPS](https://github.com/nightscout/AndroidAPS) with **one small addition**:
AAPS sends everything the loop algorithm used in each run to Nightscout, so the loop settings can be
reviewed and tuned remotely (by you, a helper, or an AI assistant) with a **read-only** Nightscout token.
Nothing else is changed. The algorithm, dosing and database are exactly the same as upstream.

Current base: **AAPS 3.4.2.6** (upstream `master`).

## What is added

Every device status uploaded to Nightscout gets an extra object `openaps.suggested.aapsTuning`:

| Field | Content |
|---|---|
| `profile` | the full profile the algorithm used: max IOB, max basal, SMB/UAM switches, max SMB minutes, targets, ISF, CR, DynISF values (TDD, insulin divisor, variable sensitivity) |
| `glucoseStatus` | glucose, delta, short and long average delta |
| `currentTemp`, `mealData`, `autosens` | running temp basal, carbs/COB/slopes, autosens ratio and details |
| `iob` | first IOB element in full and the 4 h IOB/activity curves (normal and zero temp) in 5 min steps, enough to recompute the predictions |
| `constraints` | why max IOB, basal rate or SMB were limited, and the values after constraints |
| `enact` | what was requested and what the pump actually did (TBR / SMB success, delivered amount, pump comment) |
| `pump` | last pump connection, connected, suspended, base basal |
| `runningMode` | closed / open / LGS / suspended, with reasons |
| `debug` | the algorithm debug output (the same text as in the OpenAPS tab) |
| `settings` | all APS, safety, absorption, autosens and insulin preferences, sent when they change and at least once per hour |

Size: roughly 5–8 kB per device status (every 5 min).

Safety and scope:
- Export only. Nothing is read back and nothing influences dosing.
- No database change, so you can go back to an official build at any time.
- The data sits inside `openaps.suggested`, which other apps (AAPSClient, xDrip+, Nightscout) already read and which ignore unknown keys.
- Can be switched off: **Preferences → Loop → Send tuning data to Nightscout** (on by default in this fork).
- Code: [`TuningData.kt`](plugins/aps/src/main/kotlin/app/aaps/plugins/aps/loop/TuningData.kt) plus a few lines in `LoopPlugin.kt`.

## Reading the data

Create a Nightscout token with the `readable` role only (Admin Tools → Subjects) and use API v1:

```bash
curl -s "https://YOUR-NS/api/v1/devicestatus.json?count=1&token=YOUR-READ-TOKEN" | jq '.[0].openaps.suggested.aapsTuning'
```

Never share your `API_SECRET` or a token with write permissions for this.

## Building

Build it with GitHub Actions and get the APK on Google Drive, same as the official
[browser build](https://wiki.aaps.app/en/latest/SettingUpAaps/BrowserBuild.html):

1. Set up the `KEYSTORE_SET` and `GDRIVE_OAUTH2` secrets as described in the AAPS docs (if you already build AAPS with "AAPS CI" in your fork, they are already there).
2. Actions → **AAPS Tuning CI** → Run workflow (branch `master`, variant `fullRelease`).
3. The APK lands in Google Drive in `AAPS/<version>-tuning/`.

The same keystore as "AAPS CI" is used, so the APK installs as an update over your current AAPS.
As with any AAPS update: export your settings first.

## Keeping up to date

`master` = upstream `master` + the commits of this fork. To update: merge upstream `master` and run the workflow again.

## Disclaimer

Same as AAPS itself: this is DIY software, not a medical device, used at your own risk.
The exported data is meant to support decisions about settings, together with your diabetes team.

---

# AAPS
* Check the wiki: https://wiki.aaps.app
*  Everyone who’s been looping with AAPS needs to fill out the form after 3 days of looping  https://docs.google.com/forms/d/14KcMjlINPMJHVt28MDRupa4sz4DDIooI4SrW0P3HSN8/viewform?c=0&w=1

[![Support Server](https://img.shields.io/discord/629952586895851530.svg?label=Discord&logo=Discord&colorB=7289da&style=for-the-badge)](https://discord.gg/4fQUWHZ4Mw)

[![CircleCI](https://circleci.com/gh/nightscout/AndroidAPS/tree/master.svg?style=svg)](https://circleci.com/gh/nightscout/AndroidAPS/tree/master)
[![Crowdin](https://d322cqt584bo4o.cloudfront.net/androidaps/localized.svg)](https://translations.aaps.app/project/androidaps)
[![Documentation Status](https://readthedocs.org/projects/androidaps/badge/?version=latest)](https://wiki.aaps.app/en/latest/?badge=latest)
[![codecov](https://codecov.io/gh/nightscout/AndroidAPS/branch/master/graph/badge.svg?token=EmklfIV6bH)](https://codecov.io/gh/nightscout/AndroidAPS)

DEV: 
[![CircleCI](https://circleci.com/gh/nightscout/AndroidAPS/tree/dev.svg?style=svg)](https://circleci.com/gh/nightscout/AndroidAPS/tree/dev)
[![codecov](https://codecov.io/gh/nightscout/AndroidAPS/branch/dev/graph/badge.svg?token=EmklfIV6bH)](https://codecov.io/gh/nightscout/AndroidAPS/tree/dev)

<img src="https://cdn.iconscout.com/icon/free/png-256/bitcoin-384-920569.png" srcset="https://cdn.iconscout.com/icon/free/png-512/bitcoin-384-920569.png 2x" alt="Bitcoin Icon" width="100">

3KawK8aQe48478s6fxJ8Ms6VTWkwjgr9f2
