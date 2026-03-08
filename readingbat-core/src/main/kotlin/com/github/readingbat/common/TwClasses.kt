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
 * Tailwind CSS class constants — the Tailwind equivalent of [CssNames].
 *
 * Each constant maps to one [CssNames] entry and reproduces its CSS rule
 * using `tw-` prefixed Tailwind utilities. During migration, pages switch
 * from `CssNames.X` to `TwClasses.X` one at a time.
 *
 * IMPORTANT: Always use complete, literal class name strings here.
 * Never construct class names dynamically — the Tailwind content scanner
 * uses regex and cannot evaluate Kotlin expressions.
 *
 * After migration is complete and [CssContent] is retired:
 * 1. Remove the `tw-` prefix from `tailwind.config.js`
 * 2. Find-and-replace `tw-` with `` in this file
 * 3. Delete [CssNames] and [CssContent]
 */
internal object TwClasses {
  // -- Buttons --

  /** Check Answers button: w:14em h:2em bg:#f1f1f1 font-size:115% bold rounded:6px */
  const val CHECK_ANSWERS = "tw-w-56 tw-h-8 tw-bg-rb-incomplete tw-text-[115%] tw-font-bold tw-rounded-md"

  /** Admin action button: px:1em h:2em bg:#f1f1f1 font-size:80% bold rounded:6px */
  const val ADMIN_BUTTON = "tw-px-4 tw-h-8 tw-bg-rb-incomplete tw-text-[80%] tw-font-bold tw-rounded-md"

  /** Like/dislike button: bg:#f1f1f1 w:4em h:4em rounded:6px */
  const val LIKE_BUTTONS = "tw-bg-rb-incomplete tw-w-16 tw-h-16 tw-rounded-md"

  /** Generic button: white bg, gray on hover */
  const val BTN = "tw-bg-white hover:tw-bg-gray-200"

  // -- Challenge page components --

  /** Challenge description: font-size:115% ml:1em mb:1em */
  const val CHALLENGE_DESC = "tw-text-[115%] tw-ml-4 tw-mb-4"

  /** Function column: font-size:115% */
  const val FUNC_COL = "tw-text-[115%]"

  /** Arrow column: w:2em font-size:115% text-center */
  const val ARROW = "tw-w-8 tw-text-[115%] tw-text-center"

  /** User response input: w:15em font-size:90% */
  const val USER_RESP = "tw-w-60 tw-text-[90%]"

  /** Feedback cell: w:10em border:7px solid white */
  const val FEEDBACK = "tw-w-40 tw-border-[7px] tw-border-solid tw-border-white"

  /** Status text: ml:5px font-size:115% */
  const val STATUS = "tw-ml-1 tw-text-[115%]"

  /** Success text: ml:14px font-size:115% text-black */
  const val SUCCESS = "tw-ml-3.5 tw-text-[115%] tw-text-black"

  /** "Experiment with code" link: mt:1em font-size:115% */
  const val EXPERIMENT = "tw-mt-4 tw-text-[115%]"

  /** CodingBat equivalent link: mt:2em font-size:115% */
  const val CODING_BAT = "tw-mt-8 tw-text-[115%]"

  /** Code block container: mt:2em mx:1em font-size:95% */
  const val CODE_BLOCK = "tw-mt-8 tw-mx-4 tw-text-[95%]"

  /** Kotlin playground code: mx:1em */
  const val KOTLIN_CODE = "tw-mx-4"

  // -- Dashboard / invocation grid --

  /** Dashboard table: border:1px solid #DDD collapsed */
  const val DASHBOARD = "tw-border tw-border-gray-300 tw-border-collapse"

  /** Invocation grid table: separate borders, spaced */
  const val INVOC_TABLE = "tw-border-separate tw-border-spacing-x-2.5 tw-border-spacing-y-1"

  /** Invocation grid cell: border:1px solid black, w:7px h:15px bg:#f1f1f1 */
  const val INVOC_TD = "tw-border-separate tw-border tw-border-black tw-w-[7px] tw-h-[15px] tw-bg-rb-incomplete"

  /** Invocation stat cell: pl:5px pr:1px w:10px */
  const val INVOC_STAT = "tw-pl-1 tw-pr-px tw-w-2.5"

  // -- Group / language pages --

  /** Group choice heading: font-size:155% */
  const val GROUP_CHOICE = "tw-text-[155%]"

  /** Function list item (no width): mt:1em */
  const val FUNC_ITEM1 = "tw-mt-4"

  /** Function list item (with width): mt:1em w:300px */
  const val FUNC_ITEM2 = "tw-mt-4 tw-w-[300px]"

  /** Group item card: w:300px m:15px p:10px border:1px solid gray rounded:1em */
  const val GROUP_ITEM_SRC = "tw-w-[300px] tw-m-[15px] tw-p-2.5 tw-border tw-border-gray-400 tw-rounded-2xl"

  // -- Layout utilities --

  /** Center block: display:block mx:auto w:50% */
  const val CENTER = "tw-block tw-mx-auto tw-w-1/2"

  /** Indent 1em: ml:1em */
  const val INDENT_1EM = "tw-ml-4"

  /** Indent 2em + bottom margin: ml:2em mb:2em */
  const val INDENT_2EM = "tw-ml-8 tw-mb-8"

  /** Underline text */
  const val UNDERLINE = "tw-underline"

  /** Selected tab: relative top:1px bg:white (used as id style, not class) */
  const val SELECTED_TAB = "tw-relative tw-top-px tw-bg-white"

  // -- Special cases --

  /**
   * TD_PADDING uses a descendant selector in CssContent.kt:
   *   div.tdPadding th { mt:1em pr:1em }
   *   div.tdPadding td { mt:1em pr:1em }
   *
   * This cannot be a single Tailwind class on the parent div.
   * Instead, apply these utilities directly to each th/td child:
   */
  const val TD_PADDING_CHILD = "tw-mt-4 tw-pr-4"

  // HINT has an empty CSS rule in CssContent.kt (all properties commented out).
  // No Tailwind equivalent needed — omitted intentionally.
}
