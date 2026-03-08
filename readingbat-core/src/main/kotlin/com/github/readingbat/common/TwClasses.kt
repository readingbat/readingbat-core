/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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

package com.github.readingbat.common

/**
 * Tailwind CSS class constants used across all page files.
 *
 * IMPORTANT: Always use complete, literal class name strings here.
 * Never construct class names dynamically — the Tailwind content scanner
 * uses regex and cannot evaluate Kotlin expressions.
 */
internal object TwClasses {
  // -- Buttons --

  /** Check Answers button: w:14em h:2em bg:#f1f1f1 font-size:100% bold rounded:6px */
  const val CHECK_ANSWERS =
    "w-56 h-8 bg-rb-incomplete text-[100%] font-bold rounded-md " +
      "shadow border border-gray-300 active:shadow-inner active:translate-y-px"

  /** Admin action button: px:1em h:2em bg:#f1f1f1 font-size:80% bold rounded:6px */
  const val ADMIN_BUTTON =
    "px-[14px] h-[30px] bg-rb-incomplete text-[75%] font-bold rounded-md " +
      "shadow border border-gray-300 active:shadow-inner active:translate-y-px"

  /** Like/dislike button: bg:#f1f1f1 rounded:6px border shadow */
  const val LIKE_BUTTONS =
    "bg-rb-incomplete p-[5px] rounded-md border border-gray-300 " +
      "shadow mx-[3px] active:shadow-inner active:translate-y-px"

  /** Generic button: white bg, gray on hover */
  const val BTN = "bg-white hover:bg-gray-200"

  /** Clear answer history button */
  const val CLEAR_HISTORY =
    "px-4 py-1 text-[85%] bg-rb-incomplete border border-gray-300 " +
      "rounded shadow cursor-pointer hover:bg-gray-200 active:shadow-inner active:translate-y-px"

  // -- Challenge page components --

  /** Challenge description: font-size:115% ml:1em mb:1em */
  const val CHALLENGE_DESC = "text-[115%] ml-4 mb-4"

  /** Function column: font-size:115% with vertical padding */
  const val FUNC_COL = "text-[115%] py-1"

  /** Arrow column: w:2em font-size:115% text-center px-spacing */
  const val ARROW = "w-8 text-[115%] text-center px-2"

  /** User response input: w:15em font-size:90% border rounded */
  const val USER_RESP = "w-60 text-[90%] border border-gray-400 rounded py-[5px] pr-[5px] pl-[7px]"

  /** Feedback cell: w:10em border:7px solid white */
  const val FEEDBACK = "w-40 border-[7px] border-solid border-white"

  /** Status text: ml:5px font-size:115% */
  const val STATUS = "ml-1 text-[115%]"

  /** Success text: ml:14px font-size:115% text-black */
  const val SUCCESS = "ml-3.5 text-[115%] text-black"

  /** "Experiment with code" link: mt:1em font-size:115% */
  const val EXPERIMENT = "mt-4 text-[115%]"

  /** CodingBat equivalent link: mt:2em font-size:115% */
  const val CODING_BAT = "mt-8 text-[115%]"

  /** Code block container: mt:2em mx:1em font-size:95% */
  const val CODE_BLOCK = "mt-8 mx-4 text-[95%]"

  // -- Dashboard / invocation grid --

  /** Dashboard table: border:1px solid #DDD collapsed */
  const val DASHBOARD = "border border-gray-300 border-collapse"

  /** Invocation grid table: separate borders, spaced */
  const val INVOC_TABLE = "border-separate border-spacing-x-2.5 border-spacing-y-1"

  /** Invocation grid cell: border:1px solid black, w:7px h:15px bg:#f1f1f1 */
  const val INVOC_TD = "border-separate border border-black w-[7px] h-[15px] bg-rb-incomplete"

  /** Invocation stat cell: pl:5px pr:1px w:10px */
  const val INVOC_STAT = "pl-1 pr-px w-2.5"

  // -- Group / language pages --

  /** Group choice heading: font-size:155% */
  const val GROUP_CHOICE = "text-[155%]"

  /** Function list item (no width): mt:1em */
  const val FUNC_ITEM1 = "mt-4"

  /** Function list item (with width): mt:1em w:300px */
  const val FUNC_ITEM2 = "mt-4 w-[300px]"

  /** Group item: w:322px (300px content + 2×10px padding + 2×1px border for border-box) m:15px p:10px border:1px solid gray rounded:1em */
  const val GROUP_ITEM_SRC = "w-[322px] m-[15px] p-2.5 border border-gray-500 rounded-[1em]"

  // -- Layout utilities --

  /** Center block: display:block mx:auto w:50% */
  const val CENTER = "block mx-auto w-1/2"

  /** Indent 1em: ml:1em */
  const val INDENT_1EM = "ml-4"

  /** Indent 2em + bottom margin: ml:2em mb:2em */
  const val INDENT_2EM = "ml-8 mb-8"

  /** Underline text */
  const val UNDERLINE = "underline"
}
