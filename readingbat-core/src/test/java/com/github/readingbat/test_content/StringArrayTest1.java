/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
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

package com.github.readingbat.test_content;

import static com.github.pambrose.common.util.ArrayUtils.arrayPrint;

public class StringArrayTest1 {

    public static String[] combine(String s1, String s2) {
        return new String[]{s1, s2};
    }

    public static void main(String[] args) {
        arrayPrint(combine("Car", "wash"));
        arrayPrint(combine("Hello", " world"));
        arrayPrint(combine("", ""));
    }
}