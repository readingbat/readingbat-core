/*
 * Copyright © 2026 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.readingbat.testcontent;

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
