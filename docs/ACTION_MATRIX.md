# Action Matrix

| Action | Risk | Confirmation | Required Params |
|--------|------|-------------|-----------------|
| open_app | AUTOMATIC | no | package |
| tap | LOW | no | text |
| type | LOW | no | text |
| swipe | AUTOMATIC | no | direction (optional: up/down/left/right) |
| wait | AUTOMATIC | no | durationMs (optional) |
| go_back | AUTOMATIC | no | — |
| go_home | AUTOMATIC | no | — |
| read_screen | AUTOMATIC | no | — |
| send_sms | HIGH | YES | phone, message |
| share_text | MEDIUM | no | text |
| bluetooth_on | MEDIUM | no | — |
| bluetooth_off | MEDIUM | no | — |
| bluetooth_toggle | MEDIUM | no | — |
| wifi_on | MEDIUM | no | — |
| wifi_off | MEDIUM | no | — |
| wifi_toggle | MEDIUM | no | — |
| battery_status | AUTOMATIC | no | — |
| calendar_today | LOW | no | — |
| calendar_search | LOW | no | query |

## Forbidden (always blocked)

- install_apk
- delete_system
- factory_reset
- wipe_data
- financial_transfer

## Unknown actions

- BLOCKED (no allow-by-default)
