import unittest

from utils import char

class CharTest(unittest.TestCase):
    def test_Char_letter(self):
        c = char.Char('a')
        self.assertEquals('a', str(c));
        self.assertEquals(97, int(c))
        self.assertEquals(1, len(c))
        self.assertEquals(1, c.display_width())
        self.assertEquals(1, c.display_width(5))

        
if __name__ == '__main__':
    unittest.main()
