# Формат XML пользовательских раскладок

[English version](layout_format.md)

Пользовательские клавиатурные раскладки используют формат XML, разбираемый классом [CustomLayoutLoader.java](../app/src/main/java/io/github/aroum/kazuar/inputmethod/keyboard/internal/CustomLayoutLoader.java).

> [!NOTE]
> **Прямая совместимость с внутренними раскладками**:
> Любой файл встроенной раскладки из каталога [`app/src/main/res/xml/`](../app/src/main/res/xml/) может быть загружен напрямую в качестве пользовательской раскладки без каких-либо изменений. Загрузчик (`CustomLayoutLoader`) обеспечивает обратную совместимость со встроенными ресурсами: он обрабатывает пространства имен Android (`xmlns:latin`), теги `<Row>`, `<Spacer>`, `<Key>`, стили клавиш (`enterKeyStyle`, `spaceKeyStyle`, `deleteKeyStyle`) и автоматически нормализует последовательности экранирования AAPT2 (`\\,`, `\\%`, `\\|`) в допустимый для рантайма формат.
>
> Файлы стандартных раскладок из репозитория:
> - **Русская V4**: [`rows_east_slavic_v4.xml`](../app/src/main/res/xml/rows_east_slavic_v4.xml)
> - **Русская V3**: [`rows_east_slavic_v3.xml`](../app/src/main/res/xml/rows_east_slavic_v3.xml)
> - **Русская V2**: [`rows_east_slavic_v2.xml`](../app/src/main/res/xml/rows_east_slavic_v2.xml)
> - **Русская V1**: [`rows_east_slavic.xml`](../app/src/main/res/xml/rows_east_slavic.xml)
> - **Английская V4**: [`rows_qwerty_v4.xml`](../app/src/main/res/xml/rows_qwerty_v4.xml)
> - **Английская V3**: [`rows_qwerty_v3.xml`](../app/src/main/res/xml/rows_qwerty_v3.xml)
> - **Английская V2**: [`rows_qwerty_v2.xml`](../app/src/main/res/xml/rows_qwerty_v2.xml)
> - **Английская V1**: [`rows_qwerty.xml`](../app/src/main/res/xml/rows_qwerty.xml)
> - **Символы V4**: [`rows_symbols_v4.xml`](../app/src/main/res/xml/rows_symbols_v4.xml)
> - **Символы V3**: [`rows_symbols_v3.xml`](../app/src/main/res/xml/rows_symbols_v3.xml)
> - **Раскладка редактирования и стрелок**: [`rows_editing.xml`](../app/src/main/res/xml/rows_editing.xml)
> - **num (пароль)**: [`rows_number_password.xml`](../app/src/main/res/xml/rows_number_password.xml)
> - **num (обычная)**: [`rows_number_normal.xml`](../app/src/main/res/xml/rows_number_normal.xml)
> - **Шаблон пустой пользовательской раскладки**: [`kbd_user_empty.xml`](../app/src/main/res/xml/kbd_user_empty.xml)

## Корневые атрибуты

- **`layer` / `target`**: Задает целевую раскладку:
  - `editing` (или `edit`, `arrows`): Раскладка редактирования и навигации
  - `num` (или `pin`, `number`, `numpad`): Раскладка num
  - `usr0` (или `user0`): Пользовательская раскладка 1
  - `usr1` (или `user1`): Пользовательская раскладка 2
  - `usr2` (или `user2`): Пользовательская раскладка 3
  - `ru` (или `rus`): Русская алфавитная раскладка
  - `en` (или `eng`): Английская алфавитная раскладка
- **`language` / `locale`**: Задает язык раскладки (`ru` или `en`). Если ни `layer`, ни `language` не указаны, целевая раскладка определяется по имени файла, наличию кириллицы либо через диалог выбора.
- **`keyWidth`**: Ширина клавиш по умолчанию в процентах от ширины клавиатуры (например, `10%` или `10%p`).

## Элементы

### `<Keyboard>` или `<merge>`

Корневой элемент разметки: `<Keyboard>` или `<merge>` (с опциональным объявлением `xmlns:latin="http://schemas.android.com/apk/res-auto"`).

```xml
<Keyboard language="ru" keyWidth="10%">
    ...
</Keyboard>
```
или
```xml
<merge xmlns:latin="http://schemas.android.com/apk/res-auto">
    ...
</merge>
```

### `<Row>`

Определяет горизонтальный ряд клавиш.

```xml
<Row latin:keyWidth="10%p">
    ...
</Row>
```

- **`keyWidth`**: Базовая ширина клавиш для данного ряда (например, `latin:keyWidth="10%p"`). Переопределяет ширину корневого уровня.

### `<Spacer>`

Определяет пустое пространство (отступ) между клавишами внутри `<Row>`.

```xml
<Spacer latin:keyWidth="10%p" />
```

- **`keyWidth`** (или **`width`**): Ширина отступа.

### `<Key>`

Определяет отдельную клавишу внутри `<Row>`.

- **`keyLabel`** (или **`label`**, **`keySpec`**): Основной текст или метка клавиши.
  - *Обычный символ*: `<Key keyLabel="a" />` или `latin:keySpec="a"`
  - *Хинт и метка*: Если метка содержит последовательность `\n`, символы до `\n` отображаются как визуальный хинт длительного нажатия в верхнем углу, а символы после `\n` — как основная метка.
    Пример: `keyLabel="? \n /"` отображает `/` с хинтом `?` в углу.
  - *Составная спецификация*: Поддерживается стандартный синтаксис AOSP, например `keySpec="!icon/language_switch_key|!code/key_language_switch"`, `keySpec="[{«|["` или `keySpec="↷|!code/key_redo"`.
- **`keyHintLabel`** (или **`hintLabel`**, **`hint`**): Вторичный символ в углу клавиши, вызываемый длительным нажатием (например, `latin:keyHintLabel="!"`).
- **`keyStyle`**: Ссылается на встроенные стили клавиш:
  - `spaceKeyStyle`: Настраивает клавишу пробела (иконка, код, функциональный фон).
  - `deleteKeyStyle`: Настраивает клавишу Backspace (иконка, код удаления, повтор при удержании).
  - `enterKeyStyle`: Настраивает динамическую клавишу ввода/действия.
  - `shiftKeyStyle`: Настраивает клавишу Shift.
- **`codes`** (или **`code`**): Код символа Unicode или функциональный кейкод при одиночном нажатии.
  - Если не указан, используется код UTF-32 первого символа из `keyLabel`.
  - Может быть числом (например, `codes="-10"`, `codes="32"`) или строковым идентификатором (`codes="language"`, `codes="emoji"`, `codes="clipboard"`, `codes="delete"`).
- **`keyIcon`** (или **`icon`**): Иконка вместо текстовой метки. Допустимые значения:
  - `globe` / `language`: Переключение языка (код `-10`).
  - `emoji`: Палитра эмодзи (код `-11`).
  - `clipboard`: Менеджер истории буфера обмена (код `-12`).
  - `shift`: Стрелка Shift.
  - `delete` / `backspace`: Удаление символа.
  - `space`: Индикатор пробела.
  - `return` / `enter`: Стрелка ввода.
  - `settings`: Шестеренка настроек.
- **`keyWidth`** (или **`width`**): Индивидуальная ширина клавиши в процентах (например, `keyWidth="15%"` или `keyWidth="20%p"`).
- **`longCode`**: Кейкод при длительном нажатии без открытия панели (например, `longCode="-6"` для открытия настроек или `longCode="-10"` для смены языка).
- **`moreKeys`**: Список спецификаций всплывающих клавиш через запятую (например, `latin:moreKeys="!,&#x00B2;"`).
- **`actionKey`** (или **`dynamicAction`**): При значении `"true"` (или при использовании `keyStyle="enterKeyStyle"`, `codes="action"`), клавиша становится динамической кнопкой действия и автоматически меняет иконку, надпись и поведение под активное поле ввода:
  - Однострочное поле поиска &rarr; Иконка поиска (Search)
  - Поле чата или SMS &rarr; Иконка отправки (Send)
  - Форма ввода / логина &rarr; Иконка перехода (Next / Go / Done)
  - Пользовательское действие приложения &rarr; Текстовая метка действия приложения
  - Многострочный редактор &rarr; Стандартная стрелка перевода строки (Return)
- **`mode`**: Условный фильтр режима редактора (`mode="url"`, `mode="email"`, `mode="im"` или инверсия `mode="!url,!email"`).
- **`imeAction`**: Условный фильтр по запрошенному действию IME (`imeAction="actionSearch"`, `imeAction="actionSend"`).
- **`backgroundType`**: Тип фона (`action`, `functional`, `normal`, `spacebar`).

---

### `<Replace>` (Правила автозамены по двойному нажатию)

Определяет правила автозамены при быстром двойном нажатии клавиши.

- **`from`**: Исходная последовательность символов (например, `from="чч"`).
- **`to`**: Результирующая последовательность (например, `to="ф"`).

#### Автоматическое сохранение регистра

- Правило объявляется в **нижнем регистре** (например, `<Replace from="чч" to="ц" />`).
- **Строчное нажатие**: Двойное нажатие `ч` (`чч`) вставляет `ц`.
- **Заглавное нажатие**: Двойное нажатие `Ч` с Shift или Caps Lock (`ЧЧ` или `Чч`) автоматически вставляет заглавную `Ц`.
- Объявлять отдельные правила для верхнего регистра (например, `from="ЧЧ" to="Ц"`) не требуется.

#### Ограничения

- **Двойной пробел**: В клавиатуре встроен отдельный механизм: двойное нажатие пробела вставляет точку с пробелом (`. `). Настраивается в меню параметров.
- **Двойной Backspace**: Создать правило замены для Backspace невозможно, так как клавиша удаления удаляет символы из редактора и не формирует текстовую последовательность ввода.

---

## Геометрия раскладки и ограничения

### Количество рядов
- **Нет жесткого лимита**: Допускается 3, 4, 5, 6 и более рядов (`<Row>`).
- **Динамический расчет высоты**: Общая высота клавиатуры (`baseHeight`) делится поровну между всеми рядами:
  $$\text{rowHeight} = \frac{\text{baseHeight}}{\text{totalRows}}$$
- При увеличении количества рядов высота каждого ряда пропорционально уменьшается.

### Количество колонок (клавиш в ряду)
- **Нет фиксированного лимита**: Ряд может содержать любое число клавиш и отступов.
- **Ширина 100%**: Для корректного заполнения экрана сумма ширин всех элементов в ряду должна составлять `100%` (или `100%p`). Типовые распределения:
  - 10 колонок: 10 клавиш по `10%p`
  - 11 колонок: 1 по `15%p` + 8 по `8.75%p` + 1 по `15%p`
  - 12 колонок: 12 клавиш по `8.33%p`
  - 14 колонок: 14 клавиш по `7.14%p`
- **Переполнение**: Горизонтальная прокрутка отсутствует. При сумме ширин более 100% клавиши выходят за правый край экрана. При сумме менее 100% справа остается пустое место.

### Сетка всплывающего меню (`moreKeys`)
- Всплывающее меню долгого нажатия по умолчанию отображает до 5 колонок (`mMaxMoreKeysKeyboardColumn = 5`). При большем количестве элементы переносятся на следующие строки.

---

## Функциональные кейкоды

Функциональные коды назначаются через атрибуты `codes` или `longCode` элемента `<Key>`.

### Примеры

- **Клавиша переключения языка**:
  ```xml
  <Key keyIcon="language" keyWidth="10%" />
  ```
  *Альтернативные варианты:*
  ```xml
  <Key codes="-10" keyWidth="10%" />
  <Key codes="language" keyWidth="10%" />
  <Key keyLabel="LNG" codes="-10" keyWidth="10%" />
  ```

- **Клавиша эмодзи**:
  ```xml
  <Key keyIcon="emoji" keyWidth="10%" />
  <Key keyLabel="EMO" codes="-11" keyWidth="10%" />
  ```

- **Клавиша буфера обмена**:
  ```xml
  <Key keyIcon="clipboard" keyWidth="10%" />
  <Key keyLabel="CLIP" codes="-12" keyWidth="10%" />
  ```

- **Клавиша Backspace**:
  ```xml
  <Key keyIcon="delete" keyWidth="15%" />
  ```

- **Пробел с иконкой**:
  ```xml
  <Key codes="32" keyIcon="space" keyWidth="55%" />
  ```

- **Клавиша с вызовом настроек по долгому нажатию**:
  ```xml
  <Key keyLabel="a" longCode="-6" />
  ```

### Справочная таблица функциональных кейкодов

| Идентификатор | Числовой код | Описание |
| :--- | :--- | :--- |
| **Enter** | `10` | Перевод строки (`\n`) |
| **Space** | `32` | Пробел (`' '`) |
| **Tab** | `9` | Табуляция (`\t`) |
| **Shift** | `-1` | Переключение регистра |
| **CapsLock** | `-2` | Режим Caps Lock |
| **Switch Symbols** | `-3` | Переключение алфавит / символы |
| **Backspace** | `-5` | Удаление символа перед курсором |
| **Settings** | `-6` | Открытие экрана настроек |
| **Shortcut** | `-7` | Голосовой ввод / переключение IME |
| **Language Switch** | `-10` | Переключение языка ввода |
| **Emoji** | `-11` | Палитра эмодзи |
| **Clipboard** | `-12` | Менеджер истории буфера обмена |
| **Copy** | `-320` | Копировать выделенный текст |
| **Paste** | `-321` | Вставить текст из буфера |
| **Cut** | `-322` | Вырезать выделенный текст |
| **Undo** | `-336` | Отменить последнее действие |
| **Redo** | `-338` | Повторить отмененное действие |
| **Select All** | `-324` | Выделить весь текст в поле |
| **Select Toggle** | `-310` | Включить/выключить режим выделения |
| **Delete Word** | `-337` | Удалить слово перед курсором |
| **Forward Delete** | `-342` | Удалить символ после курсора (`!code/key_forward_delete`) |
| **Switch Editing** | `-341` | Переключить раскладку редактирования (`!code/key_switch_to_edit`) |
| **Switch Usr0** | `-343` | Переключить пользовательскую раскладку 0 (`!code/key_switch_to_usr0`) |
| **Switch Usr1** | `-344` | Переключить пользовательскую раскладку 1 (`!code/key_switch_to_usr1`) |
| **Switch Usr2** | `-345` | Переключить пользовательскую раскладку 2 (`!code/key_switch_to_usr2`) |
| **Switch to ABC** | `-346` | Возврат к алфавитной раскладке (`!code/key_switch_to_abc`) |
| **Switch to num** | `-347` | Переключить раскладку num (`!code/key_switch_to_num`) |
| **Arrow Left** | `-501` | Курсор влево (`!code/key_arrow_left`) |
| **Arrow Right** | `-502` | Курсор вправо |
| **Arrow Up** | `-503` | Курсор вверх |
| **Arrow Down** | `-505` | Курсор вниз |
| **Move Home** | `-506` | Курсор в начало строки |
| **Move End** | `-507` | Курсор в конец строки |
| **Page Up** | `-508` | Прокрутка страницы вверх |
| **Page Down** | `-509` | Прокрутка страницы вниз |

---

## Символы Unicode для функциональных меток

Специальные символы Unicode, используемые в `keyLabel`:

| Символ | Код Unicode | Значение |
| :--- | :--- | :--- |
| **⌫** | `U+232B` | Backspace / стирание влево |
| **⬅** | `U+2B05` | Стрелка влево |
| **⌦** | `U+2326` | Forward Delete |
| **␡** | `U+2421` | Символ удаления символа |
| **⇥** | `U+21E5` | Табуляция вправо |
| **⇆** | `U+21C6` | Смена направления табуляции |
| **⎘** | `U+2398` | Копирование |
| **⤓** | `U+2913` | Скрыть клавиатуру |
| **▣** | `U+25A3` | Выделение текста |
| **◌** | `U+25CC` | Пунктирный маркер |
| **⛶** | `U+26F6` | Выделить всё |
| **⌕** | `U+2315` | Поиск |
| **↵** | `U+21B5` | Перевод строки (Enter) |
| **⏎** | `U+23CE` | Символ возврата каретки |
| **⎋** | `U+238B` | Escape |
| **▶** | `U+25B6` | Стрелка вправо |
| **⧉** | `U+29C9` | Многооконный режим |
| **⌸** | `U+2338` | Сетка раскладки |
| **⇱** | `U+21F1` | В начало строки (Home) |
| **⇲** | `U+21F2` | В конец строки (End) |
| **⬚** | `U+2B1A` | Квадратный заполнитель |
| **↶** | `U+21B6` | Отмена действия (Undo) |
| **↷** | `U+21B7` | Повтор действия (Redo) |
| **⌨** | `U+2328` | Основная алфавитная раскладка |
| **✕** | `U+2715` | Закрыть / очистить |
| **✖** | `U+2716` | Знак умножения |
| **⨉** | `U+2A09` | Знак оператора умножения |

---

## Динамические раскладки и условные клавиши

Kazuar поддерживает динамические раскладки, адаптирующие набор клавиш под активное приложение, тип поля ввода и действие IME. Условия задаются блоками `<switch>` / `<case>` либо атрибутами элементов `<Key>` и `<Spacer>`.

### 1. Блоки условий (`<switch>`, `<case>`, `<default>`)

Блоки `<switch>` могут размещаться на корневом уровне (переключая ряды целиком) либо внутри `<Row>` (переключая отдельные клавиши):

```xml
<Row>
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

#### Атрибуты фильтрации в `<case>`

- **`mode`**: Тип поля ввода:
  - `url`: Строка адреса браузера
  - `email`: Поле ввода email
  - `im`: Чат / мессенджер
  - `phone`: Набор телефонного номера
  - `number`: Числовые поля
  - `date` / `time` / `datetime`: Поля даты и времени
  - `text`: Обычный текст
  - *Несколько значений*: `mode="url|email"`
  - *Инверсия*: `mode="!url,!email"`
- **`imeAction`**: Запрошенное действие редактора:
  - `actionSearch` (или `search`): Поле поиска
  - `actionSend` (или `send`): Кнопка отправки
  - `actionGo` (или `go`): Кнопка перехода
  - `actionDone` (или `done`): Готово
  - `actionNext` (или `next`): Следующее поле
  - `actionPrevious` (или `previous`): Предыдущее поле
- **`isMultiLine`**: `true` для многострочного редактора, `false` для однострочного.
- **`orientation`**: Ориентация экрана (`portrait` или `landscape`).
- **`keyboardLayoutSet`**: Идентификатор набора раскладок (`qwerty`, `east_slavic`).

### 2. Условные атрибуты на уровне клавиш

Условия можно указывать непосредственно в тегах `<Key>` и `<Spacer>`:

```xml
<Key mode="url" keyLabel="/" keyWidth="10%" />
<Key mode="email" keyLabel="@" keyWidth="10%" />
<Key mode="!url,!email" keyLabel="," keyHintLabel="?" keyWidth="10%" />
```

### 3. Динамическая клавиша действия / Enter

Клавиша, меняющая внешний вид и функцию в зависимости от активного приложения:

```xml
<Key actionKey="true" keyWidth="15%" />
```
*(Эквивалентно: `keyStyle="enterKeyStyle"`, `codes="action"` или `dynamicAction="true"`)*

- Поле адреса/поиска в браузере &rarr; Иконка поиска
- Мессенджеры &rarr; Иконка отправки сообщения
- Формы регистрации &rarr; Иконка перехода (Next / Go)
- Многострочные заметки &rarr; Стандартная стрелка перевода строки
- Пользовательское действие приложения &rarr; Текстовая метка действия (например, «Найти», «Войти»)

---

## Спецификация экранирования символов и парсинга

Парсинг раскладок выполняется в два последовательных этапа:

### 1. Двухэтапный конвейер парсинга

1. **Этап 1: XML Stream Parser (`XmlPullParser`)**: Считывает XML-файл с диска. Зарезервированные символы разметки XML (`&`, `<`, `>`, `"`, `'`) должны кодироваться сущностями XML. Ошибка в структуре XML прерывает парсинг с исключением `XmlPullParserException`.
2. **Этап 2: Key Specification Engine (`MoreKeySpec.splitKeySpecs()`, `KeySpecParser`)**: Разбирает полученные строки атрибутов (`moreKeys`, `keySpec`, `keyLabel`, `keyHintLabel`), обрабатывая разделители элементов, сепараторы кодов и экранирование.

### 2. Правила синтаксиса и экранирования

#### Кодирование сущностей в атрибутах XML
В значениях любых атрибутов XML зарезервированные символы XML должны заменяться стандартными сущностями:
- `&` &rarr; `&amp;`
- `<` &rarr; `&lt;`
- `>` &rarr; `&gt;`
- `"` &rarr; `&quot;`
- `'` &rarr; `&apos;`

#### Списки всплывающих клавиш (`moreKeys`)
Атрибут `moreKeys` принимает список определений клавиш через запятую. Специальные символы в этом атрибуте:
- **Запятая (`,`)**: Разделитель элементов. Для вывода буквальной запятой требуется экранирование: `moreKeys="\,"` (в списке: `moreKeys=".,\,,?"`). Одиночная запятая `moreKeys=","` автоматически нормализуется загрузчиком в `\,`.
- **Процент (`%`)**: Маркер `ADDITIONAL_MORE_KEY_MARKER` для подстановки локализованных вариантов. Для вывода буквального знака процента требуется экранирование: `moreKeys="\%"`.
- **Вертикальная черта (`|`)**: Отделяет визуальную метку от кейкода или выводимой строки (`label|code`). Для вывода буквального символа трубы требуется экранирование: `moreKeys="\|"`.
- **Обратный слэш (`\`)**: Символ экранирования спецификаций. Для вывода буквального обратного слэша указывается четыре слэша: `moreKeys="\\\\"`.

#### Совместимость встроенного и внешнего XML (нормализация AAPT2)
- **Встроенные раскладки (`app/src/main/res/xml/`)** компилируются инструментом **AAPT2**, который «схлопывает» `\\` в `\` в таблице строк. В исходных файлах проекта поэтому используются двойные слэши: `latin:moreKeys="\\,"` и `latin:moreKeys="\\%"`.
- **Внешние раскладки (`CustomLayoutLoader`)** читаются напрямую с диска. `CustomLayoutLoader` автоматически нормализует последовательности `\\,`, `\\%` и `\\|` в `\,`, `\%` и `\|`. Файлы встроенных раскладок можно сохранять и импортировать без ручной правки слэшей.

#### Автогенерация всплывающих клавиш из `keyHintLabel`
Если на клавише задан `keyHintLabel` без явного `moreKeys`, движок генерирует спецификацию `!noPanelAutoMoreKey!<hint>`. `CustomLayoutLoader` автоматически экранирует спецсимволы (`,`, `%`, `|`), поэтому конструкция `<Key keyLabel="р" keyHintLabel="," />` корректно формирует клавишу длительного нажатия (`!noPanelAutoMoreKey!,\,`).

#### Разделение хинта и основной метки (`\n`)
Если `keyLabel` содержит последовательность `\n`, `KeySpecParser` разделяет строку на визуальный хинт в верхнем углу (до `\n`) и основную метку (после `\n`): `<Key keyLabel="? \n /" />`.

### 3. Сводная таблица правил экранирования

Таблица содержит исключительно символы, требующие экранирования:

| Символ | Контекст | Назначение / Синтаксическая роль | Необходимый синтаксис |
| :----- | :------- | :------------------------------- | :-------------------- |
| `&` | Атрибуты XML (`keyLabel`, `moreKeys` и др.) | Префикс сущности XML | `&amp;` |
| `<` | Атрибуты XML (`keyLabel`, `moreKeys` и др.) | Открытие тега XML | `&lt;` |
| `>` | Атрибуты XML (`keyLabel`, `moreKeys` и др.) | Закрытие тега XML | `&gt;` |
| `"` | Атрибуты XML (`keyLabel`, `moreKeys` и др.) | Ограничитель атрибута XML | `&quot;` |
| `'` | Атрибуты XML (`keyLabel`, `moreKeys` и др.) | Ограничитель атрибута XML | `&apos;` |
| `,` | `moreKeys` | Разделитель элементов списка | `\,` (или `\\,` в исходниках AAPT2) |
| `%` | `moreKeys` | Маркер подстановки локальных клавиш | `\%` (или `\\%` в исходниках AAPT2) |
| `\|` | `moreKeys`, `keySpec` | Сепаратор метки и кода действия | `\|` (или `\\|` в исходниках AAPT2) |
| `\` | `moreKeys` | Символ экранирования спецификаций | `\\\\` |

---

## Полные примеры раскладок

### 1. Минимальная автономная раскладка (`<Keyboard>`)

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

### 2. Расширенная раскладка по встроенному шаблону (`<merge>`)

Раскладка, совместимая со структурой файлов из [`app/src/main/res/xml/`](../app/src/main/res/xml/):

```xml
<?xml version="1.0" encoding="utf-8"?>
<merge xmlns:latin="http://schemas.android.com/apk/res-auto">
    <!-- Ряд 1: Цифры с хинтами и всплывающими клавишами -->
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

    <!-- Ряд 2: Буквы, скобки и действия -->
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

    <!-- Ряд 3: Буквы, знаки препинания с нормализацией экранирования и Backspace -->
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

    <!-- Ряд 4: Сочетания действий, отступы и пробел -->
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
