"""Подготовка текста для русской модели Silero.

Модель знает только кириллицу и немного пунктуации, всё остальное молча выбрасывает: «в 21:30» звучит
как «в», «rush B» — как тишина. Поэтому числа превращаются в слова, а латиница — в кириллицу: частые
слова чата по словарю, короткие аббревиатуры по буквам, остальное — грубой транслитерацией.
"""
import re

from num2words import num2words

# Частые слова чата: как их произносят по-русски. Ключи в нижнем регистре.
WORDS = {
    "gg": "гэ гэ", "wp": "вэ пэ", "ok": "окей", "okay": "окей", "lol": "лол", "kek": "кек", "omg": "омг",
    "wtf": "вэ тэ эф", "afk": "афк", "brb": "щас вернусь", "pls": "плиз", "plz": "плиз", "please": "плиз",
    "thx": "сенкс", "thanks": "сенкс", "hi": "хай", "hello": "хэллоу", "bye": "бай", "yes": "йес", "no": "ноу",
    "noob": "нуб", "rush": "раш", "ping": "пинг", "lag": "лаг", "bot": "бот", "game": "гейм", "play": "плей",
    "stream": "стрим", "discord": "дискорд", "youtube": "ютуб", "twitch": "твич", "steam": "стим",
    "google": "гугл", "telegram": "телеграм", "windows": "виндоус", "linux": "линукс", "cs": "кс",
    "dota": "дота", "minecraft": "майнкрафт", "valorant": "валорант",
}

# Названия английских букв — для аббревиатур вроде «B» или «PC».
LETTERS = {
    "a": "эй", "b": "би", "c": "си", "d": "ди", "e": "и", "f": "эф", "g": "джи", "h": "эйч", "i": "ай",
    "j": "джей", "k": "кей", "l": "эл", "m": "эм", "n": "эн", "o": "оу", "p": "пи", "q": "кью", "r": "ар",
    "s": "эс", "t": "ти", "u": "ю", "v": "ви", "w": "дабл ю", "x": "экс", "y": "уай", "z": "зед",
}

# Транслитерация: сначала сочетания букв, потом одиночные. Грубо, но лучше тишины.
DIGRAPHS = [
    ("sch", "ск"), ("tion", "шн"), ("sh", "ш"), ("ch", "ч"), ("zh", "ж"), ("th", "т"), ("ph", "ф"),
    ("kh", "х"), ("ck", "к"), ("qu", "кв"), ("oo", "у"), ("ee", "и"), ("ea", "и"), ("ou", "ау"),
    ("ts", "ц"), ("ya", "я"), ("yu", "ю"), ("yo", "ё"),
]
SINGLE = {
    "a": "а", "b": "б", "c": "к", "d": "д", "e": "е", "f": "ф", "g": "г", "h": "х", "i": "и", "j": "дж",
    "k": "к", "l": "л", "m": "м", "n": "н", "o": "о", "p": "п", "q": "к", "r": "р", "s": "с", "t": "т",
    "u": "у", "v": "в", "w": "в", "x": "кс", "y": "и", "z": "з",
}

MAX_NUMBER_DIGITS = 15  # длиннее — номер или мусор, читаем по цифрам
DIGITS = ["ноль", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять"]

TOKEN = re.compile(r"[A-Za-z]+|\d+(?:[.,]\d+)?")
CYRILLIC = re.compile(r"[а-яёА-ЯЁ]")


def transliterate(word: str) -> str:
    w = word.lower()
    out = []
    i = 0
    while i < len(w):
        for latin, cyrillic in DIGRAPHS:
            if w.startswith(latin, i):
                out.append(cyrillic)
                i += len(latin)
                break
        else:
            ch = w[i]
            # c перед e, i, y звучит как «с»: city, center
            out.append("с" if ch == "c" and w[i + 1:i + 2] in ("e", "i", "y") else SINGLE.get(ch, ""))
            i += 1
    return "".join(out)


def latin_word(word: str) -> str:
    lower = word.lower()
    if lower in WORDS:
        return WORDS[lower]
    if word.isupper() and len(word) <= 4:  # аббревиатура: PC, B, USA
        return " ".join(LETTERS[ch] for ch in lower)
    return transliterate(word)


def number(token: str) -> str:
    digits = token.replace(",", ".")
    if len(token) > MAX_NUMBER_DIGITS:
        return " ".join(DIGITS[int(ch)] for ch in token if ch.isdigit())
    try:
        return num2words(float(digits) if "." in digits else int(digits), lang="ru")
    except (ValueError, OverflowError, NotImplementedError):
        return " ".join(DIGITS[int(ch)] for ch in token if ch.isdigit())


def normalize(text: str) -> str:
    # «+» модель понимает как знак ударения, а «_ ~ |» — как служебные символы
    text = text.replace("+", " плюс ").replace("%", " процентов")
    text = re.sub(r"[_~|]", " ", text)
    text = TOKEN.sub(lambda m: " " + (number(m.group()) if m.group()[0].isdigit() else latin_word(m.group())) + " ",
                     text)
    return re.sub(r"\s+", " ", text).strip()


def has_speech(text: str) -> bool:
    """Осталось ли после нормализации что произносить: без кириллицы модель падает."""
    return bool(CYRILLIC.search(text))
