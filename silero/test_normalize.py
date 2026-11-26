"""Тесты нормализации; запускаются при сборке образа (см. Dockerfile)."""
import unittest

from normalize import has_speech, normalize


class NormalizeTest(unittest.TestCase):

    def test_numbers_become_words(self):
        self.assertEqual("нас будет пять человек", normalize("нас будет 5 человек"))
        self.assertEqual("в двадцать один : тридцать", normalize("в 21:30"))  # двоеточие модель читает как паузу
        self.assertEqual("две целых пять десятых", normalize("2,5"))

    def test_very_long_numbers_are_read_digit_by_digit(self):
        self.assertEqual(" ".join(["один"] * 16), normalize("1" * 16))

    def test_chat_words_come_from_the_dictionary(self):
        self.assertEqual("гэ гэ вэ пэ", normalize("gg wp"))
        self.assertEqual("скинь в дискорд", normalize("скинь в Discord"))

    def test_short_capitals_are_spelled(self):
        self.assertEqual("раш би", normalize("rush B"))
        self.assertEqual("пи си", normalize("PC"))
        self.assertEqual("кс", normalize("CS"), "словарь сильнее аббревиатуры")

    def test_other_latin_is_transliterated(self):
        self.assertEqual("шоп", normalize("shop"))
        self.assertEqual("сити", normalize("city"))

    def test_mixed_tokens_are_split(self):
        self.assertEqual("мп три", normalize("mp3"))

    def test_service_symbols_do_not_reach_the_model(self):
        self.assertEqual("си плюс плюс", normalize("C++"))
        self.assertEqual("а б", normalize("а_б"))
        self.assertEqual("пятьдесят процентов", normalize("50%"))

    def test_cyrillic_is_left_alone(self):
        self.assertEqual("Привет, как дела?", normalize("Привет, как дела?"))

    def test_nothing_to_say_without_cyrillic(self):
        self.assertFalse(has_speech(normalize("🙂 ...")))
        self.assertTrue(has_speech(normalize("gg")))


if __name__ == "__main__":
    unittest.main()
