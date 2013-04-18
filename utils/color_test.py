import unittest

from utils import color

class ColorTest(unittest.TestCase):
    def test_Color_codes(self):
        c = color.Color(color.RED, color.BOLD)
        self.assertEquals([color.BOLD, color.RED], c.get_codes());

        
if __name__ == '__main__':
    unittest.main()
