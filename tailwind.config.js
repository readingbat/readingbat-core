/** @type {import('tailwindcss').Config} */
module.exports = {
  // Scan Kotlin source files for Tailwind class names
  content: [
    "./readingbat-core/src/main/kotlin/**/*.kt",
  ],
  theme: {
    extend: {
      colors: {
        // Match existing CssContent.kt color palette
        'rb-link': '#0000DD',
        'rb-visited': '#551A8B',
        'rb-incomplete': '#F1F1F1',
        'rb-correct': '#4EAA3A',
        'rb-wrong': '#FF0000',
        'rb-header': '#419DC1',
        'rb-code-stripe': '#0600EE',
      },
      fontFamily: {
        'sans': ['verdana', 'arial', 'helvetica', 'sans-serif'],
      },
    },
  },
  prefix: '',
  plugins: [],
}
