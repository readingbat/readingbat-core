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

public class StringListTest2 {

    public static String[] combine(String[] strs) {
        String[] retval = new String[strs.length];
        for (int i = 0; i < strs.length; i++)
            retval[i] = strs[i].toUpperCase();
        return retval;
    }

    public static void main(String[] args) {
        arrayPrint(combine(new String[]{"Car", "wash"}));
        arrayPrint(combine(new String[]{"Hello", " world"}));
        arrayPrint(combine(new String[]{"Hello"}));
        arrayPrint(combine(new String[]{}));
    }
}