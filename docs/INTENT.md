# Thirai (திரை) — Intent

## Why This Exists

My mother watches 3-4 Tamil serials on Disney+ Hotstar every day. She cannot navigate a smart TV — she doesn't understand apps, menus, or search. She needs someone to start playback for her. Every single time.

This app removes that dependency.

## What It Does

A home screen widget on her phone shows posters of her shows. She taps one. The show plays on her TV. That's it.

- No app to open
- No menus to navigate
- No settings to configure
- Just: tap → watch

## How It Works (Simply)

The phone talks directly to the TV over Wi-Fi using ADB (Android Debug Bridge). When she taps a poster, the phone sends a single command to the TV: "open this show in Hotstar." The TV does the rest.

## What It Is Not

- Not a media player — the TV plays the content
- Not a remote control — it does one thing only
- Not a smart home system — no voice, no automation, no scheduling
- Not a general-purpose app — it lives and dies on the home screen widget

## Design Principles

1. **One tap. Nothing else.** The widget is the entire interface.
2. **She never opens an app.** The phone app only exists to configure the widget.
3. **Maintained remotely.** I change shows by editing a JSON file from anywhere in the world. Her phone picks it up automatically.
4. **No extra hardware.** No Raspberry Pi, no always-on computer. Just her phone and her TV on the same Wi-Fi.
5. **Works with any streaming app.** Hotstar today, YouTube or Netflix tomorrow — same widget, same tap.

## The User

- Uses a basic TV remote (channel up/down, volume, power)
- Has an Android phone (home screen widget is familiar — like speed dial for contacts)
- Watches the same 3-4 shows every day
- Needs zero technical knowledge after initial setup

## Success Criteria

She taps a poster. The show plays on the TV within 5 seconds. Every time. Without help.
