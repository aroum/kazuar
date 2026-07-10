# Custom Layout XML Format

Custom keyboard layouts use the XML format parsed by [CustomLayoutLoader.java](../app/src/main/java/io/github/sds100/keymapper/inputmethod/keyboard/internal/CustomLayoutLoader.java).

## Root Attributes

- **`language` / `locale`**: Defines the target language of the layout (e.g. `ru` or `en`). If not specified inside the root element, it is deduced from the filename.
- **`keyWidth`**: Default width of keys in percentage of keyboard width (e.g. `10%`).

## Elements

### `<Keyboard>`

The root container element.

```xml
<Keyboard language="ru" keyWidth="10%">
    ...
</Keyboard>
```

### `<Row>`

Defines a row of keys on the keyboard.

```xml
<Row>
    ...
</Row>
```

### `<Key>`

Defines a single key within a `<Row>`.

- **`keyLabel`**: The text or label displayed on the key cap.
  - *Standard Character*: `<Key keyLabel="a" />`
  - *Hint and Label*: If the label contains the literal sequence `\n`, the characters before `\n` serve as a long-press visual hint (usually rendered at the top-right of the key), and the characters after `\n` are drawn as the primary key label.
    Example: `keyLabel="? \n /"` displays `/` with `?` in the corner.
- **`codes`**: The Unicode code point or functional keycode triggered by a tap.
  - If omitted, the keyboard defaults to the UTF-32 code point of the first character in `keyLabel`.
  - For special, navigation, or functional keys, you **must** specify a custom integer value here (e.g., `codes="-5"` for Backspace).
- **`keyIcon`**: A predefined keyboard icon drawn on the key cap instead of text labels. Supported values are:
  - `shift`: The shift arrow icon.
  - `delete` / `backspace`: The delete/backspace icon.
  - `space`: The spacebar indicator.
  - `return` / `enter`: The action return/enter arrow.
  - `settings`: The gear settings icon.
  - `globe` / `language`: The language switcher globe icon.
  - `emoji`: The emoji palette smiley icon.
  - `clipboard`: The clipboard history toggle icon.
- **`keyWidth`**: Overrides the default key width. Specified as a percentage of the total keyboard width (e.g., `keyWidth="15%"`).
- **`longCode`**: The code point or keycode triggered when the key is long-pressed (e.g., `longCode="-6"` to launch settings).

---

### `<Replace>` (Double-Tap Rules)

Defines autocorrection or double-tap replacement rules.

- **`from`**: The character sequence to detect (e.g., `from="чч"`).
- **`to`**: The sequence to replace it with (e.g., `to="ф"`).

#### Case Sensitivity

XML layout replacements are **strictly case-sensitive**.

- `ЧЧ` and `чч` are **different** sequences!
- If you define `<Replace from="ЧЧ" to="Ф" />`, double-tapping lowercase `ч` (which outputs `чч`) will **not** match the rule.
- To support both upper and lowercase double-taps, you must declare both rules separately:

  ```xml
  <Replace from="чч" to="ф" />
  <Replace from="ЧЧ" to="Ф" />
  ```

#### Limitations & Preconfigured Rules

- **Space Double-Tap**: The keyboard has a built-in, preconfigured double-tap shortcut for the Spacebar. Double-tapping the Space key inserts a period followed by a space (`. `). This behavior can be enabled or disabled in the advanced settings under `Enable Double-Space period`.
- **Backspace Double-Tap**: You **cannot** define a replacement rule for a double-tapped Backspace. Double-tap replacements require characters to be typed into the text field to form a matching sequence. Because Backspace (`-5`) deletes characters instead of inserting them, it never forms a code point sequence to trigger a replacement.

---

## Special Keycodes & Examples

Below is the list of functional keycodes. To use them, assign the code to the `codes` or `longCode` attribute on a `<Key>` tag.

### Examples of Usage

- **Backspace Key with custom size and icon**:

  ```xml
  <Key keyLabel="⌫" codes="-5" keyIcon="delete" keyWidth="15%" />
  ```

- **Spacebar with Space icon and custom width**:

  ```xml
  <Key keyLabel="spc" codes="32" keyIcon="space" keyWidth="55%" />
  ```

- **Language switcher key with globe icon**:

  ```xml
  <Key keyLabel="lang" codes="-10" keyIcon="language" keyWidth="10%" />
  ```

- **Letter key that opens Settings on long press**:

  ```xml
  <Key keyLabel="a" longCode="-6" />
  ```

### Functional Keycodes Reference Table

| Key Name            | Code Value | Description                                        |
| :------------------ | :--------- | :------------------------------------------------- |
| **Enter**           | `10`       | Standard newline (`\n`)                            |
| **Space**           | `32`       | Spacebar character (`' '`)                         |
| **Tab**             | `9`        | Tab character (`\t`)                               |
| **Shift**           | `-1`       | Toggle uppercase/lowercase                         |
| **CapsLock**        | `-2`       | Caps Lock                                          |
| **Switch Symbols**  | `-3`       | Switch between alphabet and symbols/numbers layout |
| **Backspace**       | `-5`       | Delete the character before cursor                 |
| **Settings**        | `-6`       | Open keyboard settings screen                      |
| **Shortcut**        | `-7`       | Toggle voice input or shortcut IME                 |
| **Language Switch** | `-10`      | Switch to the next enabled input language          |
| **Emoji**           | `-11`      | Open the emoji keyboard palette                    |
| **Clipboard**       | `-12`      | Toggle the clipboard history manager               |
| **Copy**            | `-320`     | Copy selected text to clipboard                    |
| **Paste**           | `-321`     | Paste text from clipboard                          |
| **Cut**             | `-322`     | Cut selected text                                  |
| **Undo**            | `-336`     | Undo the last action                               |
| **Redo**            | `-338`     | Redo the last undone action                        |
| **Select All**      | `-324`     | Select all text in the active editor field         |
| **Select Toggle**   | `-310`     | Toggle selection mode                              |
| **Delete Word**     | `-337`     | Delete the entire word before cursor               |
| **Forward Delete**  | `-342`     | Delete the character after cursor                  |
| **Switch Editing**  | `-341`     | Switch to the editing/navigation layout            |
| **Arrow Left**      | `-501`     | Move cursor left                                   |
| **Arrow Right**     | `-502`     | Move cursor right                                  |
| **Arrow Up**        | `-503`     | Move cursor up                                     |
| **Arrow Down**      | `-505`     | Move cursor down                                   |
| **Move Home**       | `-506`     | Move cursor to start of line                       |
| **Move End**        | `-507`     | Move cursor to end of line                         |
| **Page Up**         | `-508`     | Scroll page up                                     |
| **Page Down**       | `-509`     | Scroll page down                                   |

---

## Recommended Unicode Symbols for Functional Labels

When you don't want to use a dynamic drawable icon via `keyIcon`, you can use these Unicode characters directly inside `keyLabel` to draw clean symbols on the keys:

| Symbol | Unicode Code | Description / Usage                |
| :----- | :----------- | :--------------------------------- |
| **⌫**  | `U+232B`     | Backspace / Delete Left            |
| **⬅**  | `U+2B05`     | Left Arrow                         |
| **⌦**  | `U+2326`     | Forward Delete                     |
| **␡**  | `U+2421`     | Delete Character                   |
| **⇥**  | `U+21E5`     | Tab Right                          |
| **⇆**  | `U+21C6`     | Tab Exchange                       |
| **📋**  | `U+1F4CB`    | Clipboard History                  |
| **⎘**  | `U+2398`     | Copy                               |
| **⤓**  | `U+2913`     | Hide Keyboard                      |
| **🔳**  | `U+1F533`    | Selection Toggle / Clear Selection |
| **◌**  | `U+25CC`     | Placeholder Circle                 |
| **⛶**  | `U+26F6`     | Fullscreen / Expand                |
| **⌕**  | `U+2315`     | Search                             |
| **↵**  | `U+21B5`     | Enter (Arrow)                      |
| **⏎**  | `U+23CE`     | Return (Symbol)                    |
| **⎋**  | `U+238B`     | Escape                             |
| **▶**  | `U+25B6`     | Play / Right Arrow                 |
| **⧉**  | `U+29C9`     | Multi-window / Swap                |
| **⌸**  | `U+2338`     | Keyboard Layout Selector           |
| **⇱**  | `U+21F1`     | Move Home (Top-Left)               |
| **⇲**  | `U+21F2`     | Move End (Bottom-Right)            |
| **⬚**  | `U+2B1A`     | Dotted Square Placeholder          |
| **↶**  | `U+21B6`     | Undo                               |
| **↷**  | `U+21B7`     | Redo                               |
| **⌨**  | `U+2328`     | Keyboard Switcher / Main Layout    |
| **✕**  | `U+2715`     | Close / Cancel                     |
| **✖**  | `U+2716`     | Heavy Multiplication X             |
| **⨉**  | `U+2A09`     | N-ary Times Operator               |

---

## Escaping Rules

Since layout configurations are standard XML files, you must follow these escaping rules for special characters:

### 1. XML Entity Escaping

Special characters that are part of XML syntax must be replaced with their corresponding XML entities:

- Ampersand (`&`) &rarr; `&amp;`
- Less than (`<`) &rarr; `&lt;`
- Greater than (`>`) &rarr; `&gt;`
- Double quote (`"`) &rarr; `&quot;`
- Single quote (`'`) &rarr; `&apos;` or `&#39;`

Example of defining a key for ampersand:

```xml
<Key keyLabel="&amp;" />
```

### 2. Percentage Sign Escaping

- **To define a literal percent sign `%` in attributes, it must be double-escaped as `\\%`** (e.g., `latin:moreKeys="\\%,&#x00B0;"`). Single escaping (`\%`) or no escaping (`%`) will cause the character to be filtered out or parsed incorrectly by the key spec parser.

### 3. Label Newlines

- When specifying a label that contains both a long-press hint and primary label, use the literal string `\n` to separate them.
  Example:

  ```xml
  <Key keyLabel="? \n /" />  <!-- '?' is hint, '/' is main label -->
  ```

## Example XML Layout File

```xml
<Keyboard language="en" keyWidth="10%">
    <Row>
        <Key keyLabel="q" />
        <Key keyLabel="w" />
        <Key keyLabel="e" />
        <Key keyLabel="r" />
        <Key keyLabel="t" />
        <Key keyLabel="y" />
        <Key keyLabel="u" />
        <Key keyLabel="i" />
        <Key keyLabel="o" />
        <Key keyLabel="p" />
    </Row>
    <Row>
        <Key keyLabel="a" />
        <Key keyLabel="s" />
        <Key keyLabel="d" />
        <Key keyLabel="f" />
        <Key keyLabel="g" />
        <Key keyLabel="h" />
        <Key keyLabel="j" />
        <Key keyLabel="k" />
        <Key keyLabel="l" />
        <Key keyLabel="⌫" keyIcon="return" keyWidth="15%" />
    </Row>
    <Replace from="--" to="—" />
</Keyboard>
```
