# BeamCard

**Read a contactless credit card with NFC and paste its details into any app - no typing, no screenshots, no internet.**

BeamCard reads the number and expiry from your contactless card using NFC, copies them to your clipboard, and leaves a notification so you can paste each field into whatever app you're filling out. It works from the app itself or straight from a Quick Settings tile.

## Why it's useful

Typing a 16-digit card number is tedious and error-prone, and mobile payment forms often block screenshot-based autofill or require fiddly manual entry. BeamCard turns a tap into a paste:

- **Tap, don't type.** Hold your phone against the card and the number is on your clipboard before you've finished reaching for the next field.
- **Perfect accuracy.** The number is read directly from the card, so it's correct every time instead of a typo you have to hunt down.
- **Expiry too.** The card's expiry date is read and copied separately, so both fields are covered.
- **Works anywhere.** Paste into e-commerce checkouts, subscriptions, banking apps, or anywhere else that asks for card details.
- **Simple to invoke from a Quick Settings tile.** Open your payment form, pull down the shade, tap **Read card**, tap your card, and paste. No need to switch apps.

## Privacy

BeamCard is privacy-respecting by design:

- **Stores nothing.** Card data exists in memory only - it's never saved, backed up, or sent anywhere (the app has no network permission at all).
- **Minimal permissions.** Just `NFC`, `VIBRATE`, and `POST_NOTIFICATIONS`, each needed for a core feature.
- **Open source.**

## How to use

### Requirements

- An Android device with an NFC reader (Android 12 / API 31 or newer).
- A contactless (tap-to-pay) credit or debit card.
- NFC enabled in your system settings (the app's home screen shows you the status and links to the settings).
- An activated NFC-enabled card of any of the major providers. (Tested with: Mastercard, Visa, Amex)

### Reading a card

1. Open **BeamCard** and tap **Read a card**.
2. Hold the back of your phone against the card until it vibrates. The app shows **Card read**.
3. The card number is copied to your clipboard automatically. A **Card ready** notification appears with two actions:
   - **Copy number** - copy the card number.
   - **Copy expiry** - copy the expiry date (formatted like in MM/YY format e.g. `12/28`).
4. In your payment form, paste the number into the card number field, tap **Copy expiry**, and paste it into the expiry field.
5. When you're done, tap **Clear clipboard** in the notification (or **Done** in the app). The clipboard and card data are also wiped automatically after ~90 seconds anyway.

### Quick Settings tile (optional)

For the fastest flow, add the **Read card** tile to your Quick Settings:

1. On the app's home screen, tap **Add Quick Settings tile**.
2. In the notification shade, tap the **pencil** (edit) icon and drag the **Read card** tile into place.
3. Next time you're on a payment form: pull down the shade, tap **Read card**, tap your card, and paste - the number is copied automatically and the tile closes itself.


## Libraries used

The app uses the [`emvnfccard`](https://github.com/devnied/EMV-NFC-Paycard-Enrollment) library to decode the card details from the NFC tag.

