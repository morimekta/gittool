import datetime
from threading import Thread, RLock, Condition
import time
import traceback


class Timer(Thread):
    def __init__(self, callback, interval):
        super(Timer, self).__init__()
        self.__callback = callback
        self.__interval = interval

        self.__start = None
        self.__end = None
        self.__mutex = RLock()
        self.__condition = Condition(self.__mutex)


    def set_interval(self, interval):
        """Change the interval time if the Timer.
        This will only affect the *next* interval period and onward.

        - interval {float} New interval time in seconds.
        """
        self.__interval = interval


    def start(self):
        """ Start the timer if it's not already running.
        
        If the timer is already running, do nothing."""
        self.__mutex.acquire()
        try:
            if self.__end != None:
                if self.__start != None:
                    return
                self.__end = None
            self.__start = time.time()
            super(Timer, self).start()
        finally:
            self.__mutex.release()


    def cancel(self):
        """Stop the timer, and wait for the timer thread to finish.
        
        Note: This will fail if called from the timer thread itself. Use stop()
        in that instance.
        """
        self.stop()
        self.join()


    def stop(self):
        """Stop the timer"""
        self.__mutex.acquire()
        try:
            self.__end = time.time()
            self.__condition.notify_all()
        finally:
            self.__mutex.release()


    def synchronized(self, cb):
        """Run an arbitrary callback to be guaranteed not to interfere with the
        timer callback.
        """
        self.__mutex.acquire()
        try:
            cb(self)
        finally:
            self.__mutex.release()

    def run(self):
        time.sleep(self.__interval)
        self.__mutex.acquire()
        try:
            while self.__end == None:
                self.__callback()
                self.__condition.wait(self.__interval)
        except:
            traceback.print_exc()
            self.__end = time.time()
        finally:
            self.__mutex.release()

    def duration(self):
        self.__mutex.acquire()
        try:
            if self.__start == None:
                return 0
            end = self.__end
            if end != None:
                return end - self.__start
            return time.time() - self.__start
        finally:
            self.__mutex.release()
