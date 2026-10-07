# Custom Layout XML Format

Custom keyboard layouts use the XML format parsed by [CustomLayoutLoader.java](../app/src/main/java/io/github/sds100/keymapper/inputmethod/keyboard/internal/CustomLayoutLoader.java).

> [!TIP]
> **Using Built-in Layouts as Templates**:
> You can take any default layout directly from the sources in [`app/src/main/res/xml/`](../app/src/main/res/xml/), modify its keys, widths, or shortcuts, and load it as a custom layout:
> - **Russian V3**: [`rows_east_slavic_v3.xml`](../app/src/main/res/xml/rows_east_slavic_v3.xml)
> - **Russian V2**: [`rows_east_slavic_v2.xml`](../app/src/main/res/xml/rows_east_slavic_v2.xml)
> - **Russian V1**: [`rows_east_slavic.xml`](../app/src/main/res/xml/rows_east_slavic.xml)
> - **English V3**: [`rows_qwerty_v3.xml`](../app/src/main/res/xml/rows_qwerty_v3.xml)
> - **English V2**: [`rows_qwerty_v2.xml`](../app/src/main/res/xml/rows_qwerty_v2.xml)
> - **English V1**: [`rows_qwerty.xml`](../app/src/main/res/xml/rows_qwerty.xml)
> - **Symbols V3**: [`rows_symbols_v3.xml`](../app/src/main/res/xml/rows_symbols_v3.xml)

## Root Attributes

- **`language` / `locale`**: Defines the target language of the layout (e.g. `ru` or `en`). If not specified inside the root element, it is deduced from the filename or Cyrillic content.
- **`keyWidth`**: Default width of keys in percentage of keyboard width (e.g. `10%` or `10%p`).

## Elements

### `<Keyboard>` or `<merge>`

The root container element can be `<Keyboard>` or `<merge>` (using `xmlns:latin="http://schemas.android.com/apk/res-auto"`).

```xml
<Keyboard language="ru" keyWidth="10%">
    ...
</Keyboard>
```
or
```xml
<merge xmlns:latin="http://schemas.android.com/apk/res-auto">
    ...
</merge>
```

### `<Row>`

Defines a row of keys on the keyboard.

```xml
<Row latin:keyWidth="10%p">
    ...
</Row>
```

- **`keyWidth`**: Sets the default key width for all keys in this row (e.g. `latin:keyWidth="10%p"`). Overrides root-level `keyWidth`.

### `<Spacer>`

Defines an empty space or gap between keys within a `<Row>`.

```xml
<Spacer latin:keyWidth="10%p" />
```

- **`keyWidth`** (or **`width`**): Width of the empty spacer.

### `<Key>`

Defines a single key within a `<Row>`.

- **`keyLabel`** (or **`label`**, **`keySpec`**): The text or label displayed on the key cap.
  - *Standard Character*: `<Key keyLabel="a" />` or `latin:keySpec="a"`
  - *Hint and Label*: If the label contains the literal sequence `\n`, the characters before `\n` serve as a long-press visual hint (usually rendered at the top-right of the key), and the characters after `\n` are drawn as the primary key label.
    Example: `keyLabel="? \n /"` displays `/` with `?` in the corner.
  - *Composite Spec*: Standard AOSP specs like `keySpec="!icon/language_switch_key|!code/key_language_switch"`, `keySpec="[{«|["` or `keySpec="↷|!code/key_redo"` are supported.
- **`keyHintLabel`** (or **`hintLabel`**, **`hint`**): The secondary symbol or character displayed in the corner of the keycap and activated on long-press (e.g., `latin:keyHintLabel="!"`).
- **`keyStyle`**: References standard built-in key styles:
  - `spaceKeyStyle`: Automatically configures spacebar icon, code, and styling.
  - `deleteKeyStyle`: Automatically configures backspace icon, delete code, repeatable action, and functional background.
  - `enterKeyStyle`: Automatically configures return/action key.
  - `shiftKeyStyle`: Configures shift key.
- **`codes`** (or **`code`**): The Unicode code point or functional keycode triggered by a tap.
  - If omitted, the keyboard defaults to the UTF-32 code point of the first character in `keyLabel`.
  - Can be specified as a numeric integer (e.g. `codes="-10"`, `codes="-11"`, `codes="-5"`) or as a symbolic name (e.g. `codes="language"`, `codes="emoji"`, `codes="clipboard"`, `codes="delete"`).
- **`keyIcon`** (or **`icon`**): A predefined keyboard icon drawn on the key cap instead of text labels. Supported values are:
  - `globe` / `language`: The language switcher globe icon (auto-assigns code `-10`).
  - `emoji`: The emoji palette smiley icon (auto-assigns code `-11`).
  - `clipboard`: The clipboard history toggle icon (auto-assigns code `-12`).
  - `shift`: The shift arrow icon.
  - `delete` / `backspace`: The delete/backspace icon.
  - `space`: The spacebar indicator.
  - `return` / `enter`: The action return/enter arrow.
  - `settings`: The gear settings icon.
- **`keyWidth`** (or **`width`**): Overrides the default key width. Specified as a percentage of the total keyboard width (e.g., `keyWidth="15%"` or `keyWidth="20%p"`).
- **`longCode`**: The code point or keycode triggered when the key is long-pressed without opening a panel (e.g., `longCode="-6"` to launch settings, or `longCode="-10"` to switch language).
- **`moreKeys`**: Comma-separated list of more keys specs or popup characters (e.g. `latin:moreKeys="!,&#x00B2;"`).
- **`keyLabelFlags`**: Label flags such as `latin:keyLabelFlags="preserveCase"` to prevent uppercase conversion when Shift is active (recommended for shortcut symbols like `↷`, `✂`, `❐`).
- **`backgroundType`**: Style of the key background (`functional`, `normal`, `spacebar`). Functional keys (Language switch, Emoji, Clipboard, Backspace, Enter, Shift, Settings) automatically receive the functional background.

---

### `<Replace>` (Double-Tap Rules)

Defines autocorrection or double-tap replacement rules.

- **`from`**: The character sequence to detect (e.g., `from="чч"`).
- **`to`**: The sequence to replace it with (e.g., `to="ф"`).

#### Automatic Case Matching

Double-tap rules automatically preserve and adapt to letter casing:

- Define the rule using **lowercase** characters (e.g. `<Replace from="чч" to="ц" />`).
- **Lowercase double-tap**: Tapping `ч` twice (`чч`) outputs lowercase `ц`.
- **Uppercase double-tap**: Tapping `Ч` with Shift or Caps Lock (`ЧЧ` or `Чч`) automatically outputs uppercase `Ц`.
- You do **not** need to declare separate uppercase rules (such as `from="ЧЧ" to="Ц"`), as the engine handles uppercase conversion dynamically.

#### Limitations & Preconfigured Rules

- **Space Double-Tap**: The keyboard has a built-in, preconfigured double-tap shortcut for the Spacebar. Double-tapping the Space key inserts a period followed by a space (`. `). This behavior can be enabled or disabled in the advanced settings under `Enable Double-Space period`.
- **Backspace Double-Tap**: You **cannot** define a replacement rule for a double-tapped Backspace. Double-tap replacements require characters to be typed into the text field to form a matching sequence. Because Backspace (`-5`) deletes characters instead of inserting them, it never forms a code point sequence to trigger a replacement.

---

## Special Keycodes & Examples

Below is the list of functional keycodes. To use them, assign the code to the `codes` or `longCode` attribute on a `<Key>` tag.

### Examples of Usage

- **Language Switcher Key with globe icon**:

  ```xml
  <Key keyIcon="language" keyWidth="10%" />
  ```

  *Alternative ways to define:*
  ```xml
  <!-- Using numeric code: standard globe icon is used automatically -->
  <Key codes="-10" keyWidth="10%" />

  <!-- Using code name -->
  <Key codes="language" keyWidth="10%" />

  <!-- With a custom text or Unicode label -->
  <Key keyLabel="🌐" codes="-10" keyWidth="10%" />
  ```

- **Emoji Key with smiley icon**:

  ```xml
  <Key keyIcon="emoji" keyWidth="10%" />
  ```

  *Alternative ways to define:*
  ```xml
  <!-- Using numeric code: standard emoji icon is used automatically -->
  <Key codes="-11" keyWidth="10%" />

  <!-- Using code name -->
  <Key codes="emoji" keyWidth="10%" />

  <!-- With a custom text or Unicode label -->
  <Key keyLabel="😀" codes="-11" keyWidth="10%" />
  ```

- **Clipboard Key with clipboard icon**:

  ```xml
  <Key keyIcon="clipboard" keyWidth="10%" />
  ```

  *Alternative ways to define:*
  ```xml
  <Key codes="-12" keyWidth="10%" />
  <Key keyLabel="📋" codes="-12" keyWidth="10%" />
  ```

- **Backspace Key with custom size and icon**:

  ```xml
  <Key keyIcon="delete" keyWidth="15%" />
  ```

- **Spacebar with Space icon and custom width**:

  ```xml
  <Key codes="32" keyIcon="space" keyWidth="55%" />
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

## Examples

### 1. Simple Standalone `<Keyboard>` Example

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
        <Key keyIcon="delete" keyWidth="10%" />
    </Row>
    <Row>
        <Key keyIcon="shift" keyWidth="15%" />
        <Key keyLabel="z" />
        <Key keyLabel="x" />
        <Key keyLabel="c" />
        <Key keyLabel="v" />
        <Key keyLabel="b" />
        <Key keyLabel="n" />
        <Key keyLabel="m" />
        <Key keyIcon="return" keyWidth="15%" />
    </Row>
    <Row>
        <Key keyIcon="language" keyWidth="15%" />
        <Key keyIcon="emoji" keyWidth="15%" />
        <Key keyIcon="space" keyWidth="55%" />
        <Key keyIcon="clipboard" keyWidth="15%" />
    </Row>
    <Replace from="--" to="—" />
</Keyboard>
```

### 2. V3 Template Style (`<merge>`, `<Spacer>`, `keyStyle`, `keyHintLabel`)

This format matches the built-in layouts directly from [`app/src/main/res/xml/`](../app/src/main/res/xml/):

```xml
<?xml version="1.0" encoding="utf-8"?>
<merge xmlns:latin="http://schemas.android.com/apk/res-auto">
    <!-- Row 1: Numbers with hint symbols on hold -->
    <Row latin:keyWidth="10%p">
        <Key latin:keySpec="1" latin:keyHintLabel="!" latin:moreKeys="!" />
        <Key latin:keySpec="2" latin:keyHintLabel="@" latin:moreKeys="@,&#x00B2;" />
        <Key latin:keySpec="3" latin:keyHintLabel="&#x2116;" latin:moreKeys="&#x2116;,&#x00B3;" />
        <Key latin:keySpec="4" latin:keyHintLabel="$" latin:moreKeys="$" />
        <Key latin:keySpec="5" latin:keyHintLabel="%" latin:moreKeys="\\%,&#x00B0;" />
        <Key latin:keySpec="6" latin:keyHintLabel="^" latin:moreKeys="^" />
        <Key latin:keySpec="7" latin:keyHintLabel="?" latin:moreKeys="?" />
        <Key latin:keySpec="8" latin:keyHintLabel="*" latin:moreKeys="*,&#x2022;,&#x00D7;" />
        <Key latin:keySpec="9" latin:keyHintLabel="(" latin:moreKeys="(" />
        <Key latin:keySpec="0" latin:keyHintLabel=")" latin:moreKeys=")" />
    </Row>

    <!-- Row 2: Letters with shortcuts and hints -->
    <Row latin:keyWidth="10%p">
        <Key latin:keySpec="й" latin:keyHintLabel="~" latin:moreKeys="~,\`" />
        <Key latin:keySpec="ц" latin:keyHintLabel="&quot;" latin:moreKeys="&quot;" />
        <Key latin:keySpec="у" latin:keyHintLabel="#" latin:moreKeys="#" />
        <Key latin:keySpec="к" latin:keyHintLabel=":" latin:moreKeys=":" />
        <Key latin:keySpec="е" latin:keyHintLabel="ё" latin:moreKeys="ё,\\\\,|" />
        <Key latin:keySpec="н" latin:keyHintLabel="↷" latin:moreKeys="↷|!code/key_redo" />
        <Key latin:keySpec="г" latin:keyHintLabel="&amp;" latin:moreKeys="&amp;" />
        <Key latin:keySpec="ш" latin:keyHintLabel="+" latin:moreKeys="+,&#x00B1;" />
        <Key latin:keySpec="з" latin:keyHintLabel="[" latin:moreKeys="[,{,«" />
        <Key latin:keySpec="х" latin:keyHintLabel="]" latin:moreKeys="],},»" />
    </Row>

    <!-- Row 3: Letters and Backspace (deleteKeyStyle) -->
    <Row latin:keyWidth="10%p">
        <Key latin:keySpec="ж" latin:keyHintLabel="⛶" latin:moreKeys="⛶|!code/key_select_all" />
        <Key latin:keySpec="ы" latin:keyHintLabel="'" latin:moreKeys="\'" />
        <Key latin:keySpec="в" />
        <Key latin:keySpec="а" latin:keyHintLabel=";" latin:moreKeys=";" />
        <Key latin:keySpec="п" latin:keyHintLabel="/" latin:moreKeys="/,&#x00F7;" />
        <Key latin:keySpec="р" latin:keyHintLabel="," latin:moreKeys="\\," />
        <Key latin:keySpec="о" latin:keyHintLabel="." latin:moreKeys="." />
        <Key latin:keySpec="л" latin:keyHintLabel="-" latin:moreKeys="-,&#x2014;,_" />
        <Key latin:keySpec="д" latin:keyHintLabel="=" latin:moreKeys="=,&#x2248;,&#x2260;" />
        <Key latin:keyStyle="deleteKeyStyle" latin:keyHintLabel="⬅" latin:moreKeys="⬅|!code/key_delete_word" />
    </Row>

    <!-- Row 4: Shortcuts, Spacers, and Spacebar (spaceKeyStyle) -->
    <Row latin:keyWidth="10%p">
        <Key latin:keySpec="я" latin:keyHintLabel="↶" latin:moreKeys="↶|!code/key_undo" />
        <Key latin:keySpec="ч" latin:keyHintLabel="✂" latin:moreKeys="✂|!code/key_cut" />
        <Key latin:keySpec="с" latin:keyHintLabel="❐" latin:moreKeys="❐|!code/key_copy" />
        <Key latin:keySpec="м" latin:keyHintLabel="⎘" latin:moreKeys="⎘|!code/key_paste" />
        <Key latin:keyStyle="spaceKeyStyle" latin:keyWidth="20%p" />
        <Key latin:keySpec="и" latin:keyHintLabel="&lt;" latin:moreKeys="&lt;" />
        <Key latin:keySpec="т" latin:keyHintLabel="&gt;" latin:moreKeys="&gt;" />
        <!-- Spacer example: empty 10% slot -->
        <!-- <Spacer latin:keyWidth="10%p" /> -->
        <Key latin:keySpec="ь" />
        <Key latin:keySpec="б" />
    </Row>
</merge>
```
