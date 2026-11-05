# Custom Layout XML Format

[Русская версия](layout_format_ru.md)

Custom keyboard layouts use the XML format parsed by [CustomLayoutLoader.java](../app/src/main/java/io/github/aroum/kazuar/inputmethod/keyboard/internal/CustomLayoutLoader.java).

> [!NOTE]
> **Direct Loading of Internal Layouts**:
> All internal layout files located in [`app/src/main/res/xml/`](../app/src/main/res/xml/) can be loaded directly as custom layouts without modification. The external layout loader (`CustomLayoutLoader`) provides full backward compatibility with internal XML resources: it processes standard Android XML namespaces (`xmlns:latin`), `<Row>`, `<Spacer>`, and `<Key>` elements, supports built-in key styles (`enterKeyStyle`, `spaceKeyStyle`, `deleteKeyStyle`), and automatically normalizes AAPT2 compile-time escape sequences (`\\,`, `\\%`, `\\|`) into valid runtime key specifications.
>
> Default repository layout files available for direct loading or modification:
> - **Russian V4**: [`rows_east_slavic_v4.xml`](../app/src/main/res/xml/rows_east_slavic_v4.xml)
> - **Russian V3**: [`rows_east_slavic_v3.xml`](../app/src/main/res/xml/rows_east_slavic_v3.xml)
> - **Russian V2**: [`rows_east_slavic_v2.xml`](../app/src/main/res/xml/rows_east_slavic_v2.xml)
> - **Russian V1**: [`rows_east_slavic.xml`](../app/src/main/res/xml/rows_east_slavic.xml)
> - **English V4**: [`rows_qwerty_v4.xml`](../app/src/main/res/xml/rows_qwerty_v4.xml)
> - **English V3**: [`rows_qwerty_v3.xml`](../app/src/main/res/xml/rows_qwerty_v3.xml)
> - **English V2**: [`rows_qwerty_v2.xml`](../app/src/main/res/xml/rows_qwerty_v2.xml)
> - **English V1**: [`rows_qwerty.xml`](../app/src/main/res/xml/rows_qwerty.xml)
> - **Symbols V4**: [`rows_symbols_v4.xml`](../app/src/main/res/xml/rows_symbols_v4.xml)
> - **Symbols V3**: [`rows_symbols_v3.xml`](../app/src/main/res/xml/rows_symbols_v3.xml)
> - **Editing / Navigation Layout**: [`rows_editing.xml`](../app/src/main/res/xml/rows_editing.xml)
> - **num (Password)**: [`rows_number_password.xml`](../app/src/main/res/xml/rows_number_password.xml)
> - **num (Normal)**: [`rows_number_normal.xml`](../app/src/main/res/xml/rows_number_normal.xml)
> - **Empty User Layout Fallback**: [`kbd_user_empty.xml`](../app/src/main/res/xml/kbd_user_empty.xml)

## Root Attributes

- **`layer` / `target`**: Defines the target layout:
  - `editing` (or `edit`, `arrows`): Editing and navigation layout
  - `num` (or `pin`, `number`, `numpad`): num layout
  - `usr0` (or `user0`): Custom User Layout 1
  - `usr1` (or `user1`): Custom User Layout 2
  - `usr2` (or `user2`): Custom User Layout 3
  - `ru` (or `rus`): Russian alphabet layout
  - `en` (or `eng`): English alphabet layout
- **`language` / `locale`**: Defines the target language of the layout (e.g. `ru` or `en`). If neither `layer` nor `language` is specified in the root element, the target is automatically determined from the file name, Cyrillic content, or selected via an interactive dialog.
- **`keyWidth`**: Default width of keys expressed as a percentage of the total keyboard width (e.g. `10%` or `10%p`).

## Elements

### `<Keyboard>` or `<merge>`

The root container element can be `<Keyboard>` or `<merge>` (with optional `xmlns:latin="http://schemas.android.com/apk/res-auto"`).

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

Defines a horizontal row of keys on the keyboard.

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

- **`keyWidth`** (or **`width`**): Width of the spacer element.

### `<Key>`

Defines an individual key within a `<Row>`.

- **`keyLabel`** (or **`label`**, **`keySpec`**): The primary text or label displayed on the keycap.
  - *Standard Character*: `<Key keyLabel="a" />` or `latin:keySpec="a"`
  - *Hint and Label*: If the label contains the literal sequence `\n`, the characters preceding `\n` serve as a long-press visual hint (rendered in the corner of the key), and the characters following `\n` are rendered as the primary key label.
    Example: `keyLabel="? \n /"` displays `/` with `?` as the corner hint.
  - *Composite Spec*: Standard AOSP specifications such as `keySpec="!icon/language_switch_key|!code/key_language_switch"`, `keySpec="[{«|["`, or `keySpec="↷|!code/key_redo"` are fully supported.
- **`keyHintLabel`** (or **`hintLabel`**, **`hint`**): The secondary symbol displayed in the corner of the keycap and triggered on long-press (e.g. `latin:keyHintLabel="!"`).
- **`keyStyle`**: References built-in key styles:
  - `spaceKeyStyle`: Configures spacebar icon, code, and functional styling.
  - `deleteKeyStyle`: Configures backspace icon, delete code, repeating action, and functional background.
  - `enterKeyStyle`: Configures dynamic return/action key.
  - `shiftKeyStyle`: Configures shift key.
- **`codes`** (or **`code`**): The Unicode code point or functional keycode triggered by tapping the key.
  - If omitted, the keyboard defaults to the UTF-32 code point of the first character in `keyLabel`.
  - Can be specified as a numeric integer (e.g. `codes="-10"`, `codes="-11"`, `codes="-5"`) or as a symbolic identifier (e.g. `codes="language"`, `codes="emoji"`, `codes="clipboard"`, `codes="delete"`).
- **`keyIcon`** (or **`icon`**): A predefined keyboard icon drawn on the keycap instead of text labels. Supported values include:
  - `globe` / `language`: Language switcher icon (assigns code `-10`).
  - `emoji`: Emoji palette icon (assigns code `-11`).
  - `clipboard`: Clipboard history manager icon (assigns code `-12`).
  - `shift`: Shift indicator icon.
  - `delete` / `backspace`: Delete / Backspace icon.
  - `space`: Spacebar indicator icon.
  - `return` / `enter`: Return / Action arrow icon.
  - `settings`: Settings gear icon.
- **`keyWidth`** (or **`width`**): Overrides the default key width. Expressed as a percentage of total keyboard width (e.g. `keyWidth="15%"` or `keyWidth="20%p"`).
- **`longCode`**: The code point or keycode triggered when the key is long-pressed without opening a popup panel (e.g. `longCode="-6"` to launch settings, or `longCode="-10"` to switch language).
- **`moreKeys`**: Comma-separated list of popup key specifications triggered on long-press (e.g. `latin:moreKeys="!,&#x00B2;"`).
- **`actionKey`** (or **`dynamicAction`**): If set to `"true"` (or when using `keyStyle="enterKeyStyle"` or `codes="action"`), marks the key as a dynamic action key. The key automatically adapts its icon, label, background color, and behavior to the active input field:
  - Single-line search field &rarr; Search icon
  - Messaging / SMS field &rarr; Send icon
  - Web form / login field &rarr; Go / Next / Done icon
  - Custom editor action &rarr; Displays the custom action label provided by the target application
  - Multi-line text editor &rarr; Standard Return / Newline arrow
- **`mode`**: Conditional filter specifying when this key is displayed (e.g. `mode="url"`, `mode="email"`, `mode="im"`, or negation `mode="!url,!email"`).
- **`imeAction`**: Conditional filter matching the active IME action (e.g. `imeAction="actionSearch"`, `imeAction="actionSend"`).
- **`backgroundType`**: Background style of the key (`action`, `functional`, `normal`, `spacebar`). Functional keys (Language switch, Emoji, Clipboard, Backspace, Enter, Shift, Settings) automatically receive the functional background. Dynamic action keys automatically receive the highlighted `action` background.

---

### `<Replace>` (Double-Tap Replacement Rules)

Defines autocorrection or double-tap replacement rules.

- **`from`**: The character sequence to detect (e.g. `from="чч"`).
- **`to`**: The sequence to replace it with (e.g. `to="ф"`).

#### Automatic Case Matching

Double-tap rules automatically preserve and adapt to letter casing:

- Rules are defined using **lowercase** characters (e.g. `<Replace from="чч" to="ц" />`).
- **Lowercase double-tap**: Tapping `ч` twice (`чч`) outputs lowercase `ц`.
- **Uppercase double-tap**: Tapping `Ч` with Shift or Caps Lock (`ЧЧ` or `Чч`) automatically outputs uppercase `Ц`.
- Declaring separate uppercase rules (such as `from="ЧЧ" to="Ц"`) is unnecessary because casing is converted dynamically at runtime.

#### Limitations & Preconfigured Rules

- **Space Double-Tap**: The keyboard includes a preconfigured shortcut for the Spacebar. Double-tapping Space inserts a period followed by a space (`. `). This behavior can be toggled in keyboard settings under `Enable Double-Space period`.
- **Backspace Double-Tap**: Double-tap replacement rules cannot be defined for Backspace. Replacements require characters to be inserted into the text connection to form an input sequence. Because Backspace (`-5`) deletes characters rather than inserting them, no input sequence is formed.

---

## Layout Geometry & Dimensions

### Number of Rows
- **No hard-coded limit**: Layouts can contain any number of `<Row>` elements (e.g. 3, 4, 5, 6, or more).
- **Dynamic height calculation**: Total usable keyboard height (`baseHeight`) is divided equally among all rows in the layout:
  $$\text{rowHeight} = \frac{\text{baseHeight}}{\text{totalRows}}$$
- **Proportional scaling**: Increasing the number of rows proportionally reduces the vertical height of individual rows to keep the overall keyboard within defined boundaries.

### Number of Columns (Keys per Row)
- **No fixed column limit**: A row can contain any number of `<Key>` and `<Spacer>` elements. Keys are placed horizontally from left to right:
  $$\text{keyWidth} = \text{round}(\text{wPercent} \times \text{baseWidth}), \quad x \leftarrow x + \text{keyWidth}$$
- **Full row width (100%)**: To align keys across the entire display width without gaps or overflow, the sum of key and spacer widths in each row should equal `100%` (or `100%p`). Typical column distributions:
  - 10 columns: 10 keys at `10%p`
  - 11 columns: 1 key at `15%p` + 8 keys at `8.75%p` + 1 key at `15%p`
  - 12 columns: 12 keys at `8.33%p`
  - 14 columns: 14 keys at `7.14%p`
- **Overflow behavior**: The keyboard view does not support horizontal scrolling. If the sum of widths exceeds 100%, trailing keys extend beyond the right screen margin. If the sum is less than 100%, empty space remains on the right side of the row.

### Popup Keys Grid Limit (`moreKeys`)
- The long-press popup mini-keyboard displays up to 5 columns by default (`mMaxMoreKeysKeyboardColumn = 5`). Additional keys wrap into subsequent rows within the popup container.

---

## Special Keycodes & Examples

Functional keycodes can be assigned to the `codes` or `longCode` attributes of any `<Key>` element.

### Examples

- **Language Switcher Key**:
  ```xml
  <Key keyIcon="language" keyWidth="10%" />
  ```
  *Alternative declarations:*
  ```xml
  <!-- Using numeric code (globe icon assigned automatically) -->
  <Key codes="-10" keyWidth="10%" />

  <!-- Using symbolic code name -->
  <Key codes="language" keyWidth="10%" />

  <!-- Using custom text label -->
  <Key keyLabel="LNG" codes="-10" keyWidth="10%" />
  ```

- **Emoji Key**:
  ```xml
  <Key keyIcon="emoji" keyWidth="10%" />
  ```
  *Alternative declarations:*
  ```xml
  <Key codes="-11" keyWidth="10%" />
  <Key codes="emoji" keyWidth="10%" />
  <Key keyLabel="EMO" codes="-11" keyWidth="10%" />
  ```

- **Clipboard Key**:
  ```xml
  <Key keyIcon="clipboard" keyWidth="10%" />
  ```
  *Alternative declarations:*
  ```xml
  <Key codes="-12" keyWidth="10%" />
  <Key keyLabel="CLIP" codes="-12" keyWidth="10%" />
  ```

- **Backspace Key**:
  ```xml
  <Key keyIcon="delete" keyWidth="15%" />
  ```

- **Spacebar with Space Icon**:
  ```xml
  <Key codes="32" keyIcon="space" keyWidth="55%" />
  ```

- **Letter Key Opening Settings on Long-Press**:
  ```xml
  <Key keyLabel="a" longCode="-6" />
  ```

### Functional Keycodes Reference Table

| Key Identifier      | Numeric Code | Description                                        |
| :------------------ | :----------- | :------------------------------------------------- |
| **Enter**           | `10`         | Standard newline character (`\n`)                  |
| **Space**           | `32`         | Spacebar character (`' '`)                         |
| **Tab**             | `9`          | Tabulation character (`\t`)                        |
| **Shift**           | `-1`         | Toggle uppercase/lowercase mode                    |
| **CapsLock**        | `-2`         | Caps Lock state                                    |
| **Switch Symbols**  | `-3`         | Switch between alphabet and symbols/numbers layout |
| **Backspace**       | `-5`         | Delete character preceding cursor                  |
| **Settings**        | `-6`         | Open keyboard settings activity                    |
| **Shortcut**        | `-7`         | Voice input or shortcut IME                        |
| **Language Switch** | `-10`        | Switch to next enabled input language              |
| **Emoji**           | `-11`        | Open emoji palette                                 |
| **Clipboard**       | `-12`        | Toggle clipboard history manager                   |
| **Copy**            | `-320`       | Copy selected text to clipboard                    |
| **Paste**           | `-321`       | Paste text from clipboard                          |
| **Cut**             | `-322`       | Cut selected text                                  |
| **Undo**            | `-336`       | Undo previous editor action                        |
| **Redo**            | `-338`       | Redo previous undone action                        |
| **Select All**      | `-324`       | Select all text in active editor field             |
| **Select Toggle**   | `-310`       | Toggle text selection mode                         |
| **Delete Word**     | `-337`     | Delete word preceding cursor                       |
| **Forward Delete**  | `-342`       | Delete character following cursor (`!code/key_forward_delete`) |
| **Switch Editing**  | `-341`       | Toggle editing/navigation layout (`!code/key_switch_to_edit` or `!code/key_switch_to_editing`) |
| **Switch Usr0**     | `-343`       | Toggle custom user layout 0 (`!code/key_switch_to_usr0`) |
| **Switch Usr1**     | `-344`       | Toggle custom user layout 1 (`!code/key_switch_to_usr1`) |
| **Switch Usr2**     | `-345`       | Toggle custom user layout 2 (`!code/key_switch_to_usr2`) |
| **Switch to ABC**   | `-346`       | Unconditional return to alphabet layout (`!code/key_switch_to_abc`) |
| **Switch to num**   | `-347`       | Toggle num layout (`!code/key_switch_to_num`) |
| **Arrow Left**      | `-501`       | Move cursor left (`!code/key_arrow_left`)          |
| **Arrow Right**     | `-502`       | Move cursor right                                  |
| **Arrow Up**        | `-503`       | Move cursor up                                     |
| **Arrow Down**      | `-505`       | Move cursor down                                   |
| **Move Home**       | `-506`       | Move cursor to beginning of line                   |
| **Move End**        | `-507`       | Move cursor to end of line                         |
| **Page Up**         | `-508`       | Scroll view up by page                             |
| **Page Down**       | `-509`       | Scroll view down by page                           |

---

## Unicode Symbols for Functional Labels

When custom drawable icons are not desired, the following standard Unicode characters can be used in `keyLabel` to represent editing and control actions:

| Symbol | Unicode Point | Action / Meaning                   |
| :----- | :------------ | :--------------------------------- |
| **⌫**  | `U+232B`      | Backspace / Erase Left             |
| **⬅**  | `U+2B05`      | Left Navigation Arrow              |
| **⌦**  | `U+2326`      | Forward Delete                     |
| **␡**  | `U+2421`      | Delete Character Symbol            |
| **⇥**  | `U+21E5`      | Tab Forward                        |
| **⇆**  | `U+21C6`      | Tab Left/Right Exchange            |
| **⎘**  | `U+2398`      | Copy / Next Page                   |
| **⤓**  | `U+2913`      | Dismiss / Hide Keyboard            |
| **▣**  | `U+25A3`      | Selection Box                      |
| **◌**  | `U+25CC`      | Dotted Placeholder Circle          |
| **⛶**  | `U+26F6`      | Select All / Square Four Corners   |
| **⌕**  | `U+2315`      | Search Indicator                   |
| **↵**  | `U+21B5`      | Carriage Return / Enter            |
| **⏎**  | `U+23CE`      | Return Symbol                      |
| **⎋**  | `U+238B`      | Escape                             |
| **▶**  | `U+25B6`      | Play / Right Arrow                 |
| **⧉**  | `U+29C9`      | Window Overlap / Dual Mode         |
| **⌸**  | `U+2338`      | Keyboard Layout Grid               |
| **⇱**  | `U+21F1`      | Navigate Home (Top-Left)           |
| **⇲**  | `U+21F2`      | Navigate End (Bottom-Right)        |
| **⬚**  | `U+2B1A`      | Dotted Square Placeholder          |
| **↶**  | `U+21B6`      | Undo Action                        |
| **↷**  | `U+21B7`      | Redo Action                        |
| **⌨**  | `U+2328`      | Main Alphabet Keyboard             |
| **✕**  | `U+2715`      | Close / Clear                      |
| **✖**  | `U+2716`      | Heavy Multiplication Cross         |
| **⨉**  | `U+2A09`      | N-ary Times Operator               |

---

## Dynamic Layouts & Conditional Keys

Kazuar supports dynamic keyboard configurations that adapt keys to the active application, editor input mode, and IME actions. Conditions can be declared via structural `<switch>` / `<case>` blocks or directly on individual `<Key>` and `<Spacer>` elements.

### 1. Conditional Containers (`<switch>`, `<case>`, `<default>`)

`<switch>` containers can be placed at the root level (to switch entire rows or layout fragments) or inside a `<Row>` (to switch specific keys):

```xml
<Row>
    <!-- Switch between URL slash, Email at-sign, and standard comma -->
    <switch>
        <case mode="url">
            <Key keyLabel="/" keyHintLabel="?" />
        </case>
        <case mode="email">
            <Key keyLabel="@" />
        </case>
        <default>
            <Key keyLabel="," keyHintLabel="?" />
        </default>
    </switch>
    ...
</Row>
```

#### Supported `<case>` Filter Attributes

- **`mode`**: Matches the input field type reported by the target application:
  - `url`: Web browser address bars and URL inputs
  - `email`: Email address input fields
  - `im`: Messaging and instant chat inputs
  - `phone`: Dialpad and telephone inputs
  - `number`: Numeric input fields
  - `date` / `time` / `datetime`: Date and time inputs
  - `text`: Standard text fields
  - *Multiple criteria*: `mode="url|email"` matches if the mode is either URL or Email.
  - *Negation*: `mode="!url,!email"` matches fields that are neither URL nor Email.
- **`imeAction`**: Matches the IME action specified by the editor:
  - `actionSearch` (or `search`): Search inputs
  - `actionSend` (or `send`): Send button in messaging applications
  - `actionGo` (or `go`): Go button in web forms
  - `actionDone` (or `done`): Done action in single-line forms
  - `actionNext` (or `next`): Next field action in multi-input forms
  - `actionPrevious` (or `previous`): Previous field action
- **`isMultiLine`**: `true` for multi-line text views, `false` for single-line inputs.
- **`orientation`**: Matches device screen orientation (`portrait` or `landscape`).
- **`keyboardLayoutSet`**: Matches the layout name identifier (e.g. `qwerty`, `east_slavic`).

### 2. Key-Level Conditional Attributes

As a shorthand alternative to `<switch>` containers, conditions can be assigned directly to `<Key>` or `<Spacer>` elements:

```xml
<!-- Visible only in URL mode -->
<Key mode="url" keyLabel="/" keyWidth="10%" />

<!-- Visible only in Email mode -->
<Key mode="email" keyLabel="@" keyWidth="10%" />

<!-- Visible in all other modes (neither URL nor Email) -->
<Key mode="!url,!email" keyLabel="," keyHintLabel="?" keyWidth="10%" />
```

### 3. Dynamic Action / Enter Key

To define an action key that automatically adapts its appearance and function to the active field:

```xml
<Key actionKey="true" keyWidth="15%" />
```
*(Equivalent declarations: `keyStyle="enterKeyStyle"`, `codes="action"`, or `dynamicAction="true"`)*

- Web browser address bar &rarr; Search icon
- Messaging applications &rarr; Send icon
- Web input forms &rarr; Next or Go action
- Multi-line text editors &rarr; Standard Return / Newline arrow
- Custom application actions &rarr; Renders the custom action label provided by the target application (e.g. "Join", "Login") with highlighted `action` background styling.

---

## Character Escaping & Parser Specifications

The layout engine processes layout definitions through a two-stage parsing pipeline:

### 1. Two-Stage Parsing Pipeline

1. **Stage 1: XML Stream Parser (`XmlPullParser`)**: Reads the XML document tree from disk or resources. Reserved XML markup characters (`&`, `<`, `>`, `"`, `'`) must be encoded using standard XML entities. Any malformed XML entity halts parsing with an `XmlPullParserException`.
2. **Stage 2: Key Specification Engine (`MoreKeySpec.splitKeySpecs()`, `KeySpecParser`)**: Takes the resolved attribute strings (`moreKeys`, `keySpec`, `keyLabel`, `keyHintLabel`) and parses delimiters, functional action codes, and popup item lists.

### 2. Syntax & Escaping Rules

#### XML Attribute Entity Encoding
Standard XML entities must be used in attribute values when declaring XML syntax characters:
- `&` &rarr; `&amp;`
- `<` &rarr; `&lt;`
- `>` &rarr; `&gt;`
- `"` &rarr; `&quot;`
- `'` &rarr; `&apos;`

#### Popup Key Lists (`moreKeys`)
The `moreKeys` attribute accepts a comma-separated list of popup key definitions. Several characters serve syntactic functions within this attribute:
- **Comma (`,`)**: Functions as the popup item delimiter. To define a literal comma in `moreKeys`, escape it with a backslash: `moreKeys="\,"` (or within a list: `moreKeys=".,\,,?"`). Standalone unescaped commas (`moreKeys=","`) are automatically normalized to `\,` by `CustomLayoutLoader`.
- **Percent (`%`)**: Functions as `ADDITIONAL_MORE_KEY_MARKER` for injecting locale-specific variants. To define a literal percent sign in a popup key, escape it: `moreKeys="\%"`.
- **Vertical Bar (`|`)**: Separates a key's visual specification from its functional action code or output string (`label|code`). To define a literal pipe character in a popup key, escape it: `moreKeys="\|"`.
- **Backslash (`\`)**: Acts as the escape operator for the specification parser. To output a literal backslash from `moreKeys`, declare four backslashes: `moreKeys="\\\\"`.

#### Built-in vs External XML Compatibility (AAPT2 Normalization)
- **Built-in layouts (`app/src/main/res/xml/`)** are processed at build time by Android's Asset Packaging Tool (**AAPT2**). AAPT2 consumes backslashes in string tables, converting `\\` into `\`. Source files in the repository therefore specify `latin:moreKeys="\\,"` and `latin:moreKeys="\\%"`.
- **External layouts (`CustomLayoutLoader`)** are loaded directly from disk at runtime. To ensure complete interoperability, `CustomLayoutLoader` automatically normalizes `\\,`, `\\%`, and `\\|` into `\,`, `\%`, and `\|`. Internal XML files can therefore be copied and imported directly without modifying escape sequences.

#### Automatic Popup Generation from `keyHintLabel`
When `keyHintLabel` is specified without an explicit `moreKeys` attribute, the engine automatically synthesizes a long-press popup key (`!noPanelAutoMoreKey!<hint>`). `CustomLayoutLoader` automatically escapes reserved delimiter characters (`,`, `%`, `|`) so that keys such as `<Key keyLabel="р" keyHintLabel="," />` produce valid popup keys (`!noPanelAutoMoreKey!,\,`).

#### Corner Hint and Primary Label Splitting (`\n`)
When `keyLabel` contains the literal sequence `\n`, `KeySpecParser` splits the text into a corner visual hint (preceding `\n`) and a primary key label (following `\n`): `<Key keyLabel="? \n /" />`.

### 3. Escaping Reference Table

The table below lists only the characters that require escaping, their target attribute context, and the required syntax:

| Character | Context | Syntactic Function | Required Syntax |
| :-------- | :------ | :----------------- | :-------------- |
| `&` | XML Attributes (`keyLabel`, `moreKeys`, etc.) | XML entity prefix | `&amp;` |
| `<` | XML Attributes (`keyLabel`, `moreKeys`, etc.) | XML element tag opening | `&lt;` |
| `>` | XML Attributes (`keyLabel`, `moreKeys`, etc.) | XML element tag closing | `&gt;` |
| `"` | XML Attributes (`keyLabel`, `moreKeys`, etc.) | XML attribute delimiter | `&quot;` |
| `'` | XML Attributes (`keyLabel`, `moreKeys`, etc.) | XML attribute delimiter | `&apos;` |
| `,` | `moreKeys` | List item delimiter | `\,` (or `\\,` in AAPT2 sources) |
| `%` | `moreKeys` | Placeholder marker for locale keys | `\%` (or `\\%` in AAPT2 sources) |
| `\|` | `moreKeys`, `keySpec` | Label and action code separator | `\|` (or `\\|` in AAPT2 sources) |
| `\` | `moreKeys` | Specification escape character | `\\\\` |

---

## Complete Layout Examples

### 1. Minimal Standalone Layout (`<Keyboard>`)

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

### 2. Advanced Layout Matching Internal Template (`<merge>`)

This layout demonstrates direct compatibility with internal resource files in [`app/src/main/res/xml/`](../app/src/main/res/xml/):

```xml
<?xml version="1.0" encoding="utf-8"?>
<merge xmlns:latin="http://schemas.android.com/apk/res-auto">
    <!-- Row 1: Numbers with hint symbols and popup keys -->
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

    <!-- Row 2: Letters with action keycodes and brackets -->
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

    <!-- Row 3: Letters, punctuation with escape normalization, and Backspace -->
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

    <!-- Row 4: Shortcuts, Spacers, and Spacebar -->
    <Row latin:keyWidth="10%p">
        <Key latin:keySpec="я" latin:keyHintLabel="↶" latin:moreKeys="↶|!code/key_undo" />
        <Key latin:keySpec="ч" latin:keyHintLabel="✂" latin:moreKeys="✂|!code/key_cut" />
        <Key latin:keySpec="с" latin:keyHintLabel="❐" latin:moreKeys="❐|!code/key_copy" />
        <Key latin:keySpec="м" latin:keyHintLabel="⎘" latin:moreKeys="⎘|!code/key_paste" />
        <Key latin:keyStyle="spaceKeyStyle" latin:keyWidth="20%p" />
        <Key latin:keySpec="и" latin:keyHintLabel="&lt;" latin:moreKeys="&lt;" />
        <Key latin:keySpec="т" latin:keyHintLabel="&gt;" latin:moreKeys="&gt;" />
        <Key latin:keySpec="ь" />
        <Key latin:keySpec="б" />
    </Row>
</merge>
```
