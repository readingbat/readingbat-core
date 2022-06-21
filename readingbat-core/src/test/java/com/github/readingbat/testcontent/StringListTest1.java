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

package com.github.readingbat.testcontent;

import java.util.ArrayList;
import java.util.List;

import static com.github.pambrose.common.util.ListUtils.listPrint;

public class StringListTest1 {

    public static List<String> combine(String s1, String s2) {
        List<String> retval = new ArrayList<>();
        retval.add(s1);
        retval.add(s2);
        return retval;
    }

    public static void main(String[] args) {
        listPrint(combine("Car", "wash"));
        listPrint(combine("Hello", " world"));
        listPrint(combine("", ""));
    }
}