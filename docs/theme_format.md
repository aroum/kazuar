# Custom Theme JSON Format

This document describes the JSON structure for creating custom keyboard themes. Both light and dark modes are specified in a single JSON file.

## JSON Schema

```json
{
  "theme_name": "My Custom Theme",
  "light": {
    "keyboard_background": "#fbf1c7",
    "key_background": "#ebdbb2",
    "key_background_pressed": "#d5c4a1",
    "key_text_color": "#3c3836",
    "functional_key_background": "#d5c4a1",
    "functional_key_background_pressed": "#bdae93",
    "functional_key_text_color": "#3c3836",
    "key_hint_color": "#928374",
    "key_border_color": "#a89984"
  },
  "dark": {
    "keyboard_background": "#282828",
    "key_background": "#3c3836",
    "key_background_pressed": "#504945",
    "key_text_color": "#ebdbb2",
    "functional_key_background": "#504945",
    "functional_key_background_pressed": "#665c54",
    "functional_key_text_color": "#ebdbb2",
    "key_hint_color": "#928374",
    "key_border_color": "#7c6f64"
  }
}
```

## Attribute Explanations

| Attribute                           | Description                                                                                     |
| :---------------------------------- | :---------------------------------------------------------------------------------------------- |
| `keyboard_background`               | The background color of the keyboard panel.                                                     |
| `key_background`                    | The background color of regular keys in their normal state.                                     |
| `key_background_pressed`            | The background color of regular keys when pressed.                                              |
| `key_text_color`                    | The text color of regular key labels.                                                           |
| `functional_key_background`         | The background color of functional keys (Shift, Backspace, Space, Enter) in their normal state. |
| `functional_key_background_pressed` | The background color of functional keys when pressed.                                           |
| `functional_key_text_color`         | The text color of functional key labels.                                                        |
| `key_hint_color`                    | The text color of key hints (character popups or long-press hints).                             |
| `key_border_color`                  | The border color of keys (only visible if "Key borders" is enabled in settings).                |
