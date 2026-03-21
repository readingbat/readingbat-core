package com.github.readingbat.testcontent;

// @desc **charAt()** returns a single character — remember that indexing starts at **0**.

public class CharAt1 {

    public static char getIt(String s, int index) {
        char result = s.charAt(index);
        return result;
    }

    public static void main(String[] args) {
        System.out.println(getIt("hello", 0));
        System.out.println(getIt("hello", 4));
        System.out.println(getIt("java", 2));
        System.out.println(getIt("abcdef", 3));
        System.out.println(getIt("world", 1));
        System.out.println(getIt("xyz", 2));
        System.out.println(getIt("banana", 5));
    }
}
