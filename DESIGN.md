---
name: CHM Reader
description: A quiet native workspace for reading original technical documents.
colors:
  light-primary: "#006B5F"
  light-on-primary: "#FFFFFF"
  light-primary-container: "#A0F2DF"
  light-on-primary-container: "#00201B"
  dark-primary: "#82D5C3"
  dark-on-primary: "#00382F"
  dark-primary-container: "#005144"
  dark-on-primary-container: "#A0F2DF"
  light-secondary-container: "#DAE5DF"
  light-on-secondary-container: "#131D19"
  dark-secondary-container: "#354B43"
  dark-on-secondary-container: "#D5E8DF"
  light-surface: "#F7FAF8"
  light-on-surface: "#17201C"
  light-on-surface-variant: "#414943"
  light-outline: "#707972"
  dark-surface: "#101D1A"
  dark-on-surface: "#DEE5DF"
  dark-on-surface-variant: "#BFC9C2"
  dark-outline: "#89938C"
  document-canvas: "#FFFFFF"
typography:
  welcome-title:
    fontFamily: "sans"
    fontSize: "30sp"
    fontWeight: 700
  section-title:
    fontFamily: "sans"
    fontSize: "22sp"
    fontWeight: 400
  welcome-body:
    fontFamily: "sans"
    fontSize: "18sp"
    fontWeight: 400
  result-title:
    fontFamily: "sans"
    fontSize: "17sp"
    fontWeight: 700
  tree-body:
    fontFamily: "sans"
    fontSize: "16sp"
    fontWeight: 400
  supporting:
    fontFamily: "sans"
    fontSize: "14sp"
    fontWeight: 400
  metadata:
    fontFamily: "sans"
    fontSize: "13sp"
    fontWeight: 400
spacing:
  compact: "4dp"
  small: "8dp"
  control-inset: "12dp"
  standard: "16dp"
  sheet-inset: "20dp"
  generous: "24dp"
  welcome-inset: "28dp"
components:
  button-primary:
    backgroundColor: "{colors.light-primary}"
    textColor: "{colors.light-on-primary}"
  button-outlined:
    backgroundColor: "transparent"
    textColor: "{colors.light-primary}"
  icon-action:
    textColor: "{colors.light-on-surface}"
    padding: "{spacing.control-inset}"
    size: "48dp"
  tree-row:
    textColor: "{colors.light-on-surface}"
    typography: "{typography.tree-body}"
    padding: "8dp 12dp 8dp 0"
  tree-row-current:
    backgroundColor: "{colors.light-primary-container}"
    textColor: "{colors.light-on-surface}"
    typography: "{typography.tree-body}"
  search-field:
    textColor: "{colors.light-on-surface}"
  toolbar:
    backgroundColor: "{colors.light-surface}"
    height: "64dp"
  history-navigation:
    backgroundColor: "{colors.light-surface}"
    textColor: "{colors.light-on-surface}"
    typography: "{typography.metadata}"
    height: "48dp"
  resize-region:
    width: "48dp"
  reader:
    backgroundColor: "{colors.document-canvas}"
---

# Design System: CHM Reader

## Overview

**Creative North Star: "Quiet reading workspace"**

This native Android interface uses Material 3 for the controls around an original technical document. Read and Operate are the guiding surface modes: the document occupies the available reading area, while the contents tree, search, history and settings support deliberate navigation. CHM Sharp is a workflow reference, not an asset source.

The app chrome is calm, compact and teal. The document retains its own HTML typography, colors, images and tables. These rules describe the implementation in `MainActivity.kt`, `ui/TocAdapter.kt`, `ui/SearchPanel.kt`, `ui/Icons.kt` and the two theme resources. This is a source-based record; Android visual, touch and assistive-technology verification remains outstanding because the emulator did not boot.

**Key Characteristics:**
- Native Material 3 controls with system typography.
- A white WebView canvas and unchanged document colors in both app themes.
- A real contents hierarchy with independent expansion and page actions.
- A phone drawer that becomes an adjustable persistent panel in wide windows.

## Colors

Muted green surfaces support a teal accent in light mode; the dark theme deepens the green surface and lightens the accent. The frontmatter records the exact values declared in `values/themes.xml` and `values-night/themes.xml`. Components use matching light or dark roles at runtime; frontmatter component examples show light mode.

### Primary

- **Deep teal / pale teal:** primary roles are the app's action accents, with matching on-primary text roles.
- **Mint selection / forest selection:** primary containers mark the current contents row. That row explicitly retains on-surface text and becomes bold; it does not explicitly use on-primary-container.
- Matching on-primary-container roles remain part of the native theme for Material components.

### Secondary

- **Soft sage / shaded sage:** secondary-container pairs are declared for Material's native tonal controls. Custom reader views do not explicitly assign them.

### Neutral

- **Green paper / deep green:** surface roles form the root, contents panel and welcome area.
- **Ink / pale ink:** on-surface roles color normal app text and custom vector icons.
- **Muted ink:** on-surface-variant roles support welcome copy and the contents count.
- **Quiet outline:** outline roles form the visible splitter line and support inherited Material outlines.
- **Document white:** the WebView background is white. An HTML page may supply its own background.

**The Document Colors Rule.** App theme changes affect chrome. Preserve the document's colors and disable WebView automatic darkening.

## Typography

**App Font:** Android `sans`, normally the platform Roboto family; no bundled or downloaded font.

The application uses practical system type and native Material text appearances. Explicit sizes use `sp`, allowing Android font scaling. Line height, tracking, toolbar typography and native control typography are inherited rather than overridden. Do not infer fixed CSS line-height tokens from this record.

### Hierarchy

- **Welcome title:** `welcome-title`, bold; the initial library heading.
- **Section title:** `section-title`, regular; contents and search headings.
- **Welcome body:** `welcome-body`, regular; introductory copy.
- **Result title:** `result-title`, bold; search-result names.
- **Tree body:** `tree-body`, regular; contents labels. The current page label changes to bold.
- **Supporting:** `supporting`, regular; search excerpts/status, import status, highlight controls, welcome support copy and license text.
- **Metadata:** `metadata`, regular; contents counts and the bottom current-page label.

Document type remains under its own HTML/CSS. The text-size chooser changes WebView text zoom independently, using 80%, 100%, 120%, 150%, 180% and 200% choices; the default is 100%.

## Layout

The root is a vertical workspace: an app bar (64dp), an optional import indicator (4dp) and status row, the main reader/drawer region, an optional in-page highlight row, and history navigation. Insets account for system bars, display cutouts and the keyboard. The reader consumes the remaining height.

At less than 840dp of available width, contents use a start drawer whose width is the smaller of 360dp and 90% of the root width. The tree and document occupy the available height; content rows may grow with text.

At 840dp or more after horizontal root insets, contents become a persistent panel. Its saved initial width defaults to 320dp, with a 240dp minimum. Its maximum is the available width minus 368dp: a 48dp resize region plus a 320dp minimum reader. Resizing and subsequent window changes clamp to these limits. The resize region contains one centered outline line (1dp); the entire region is interactive. A click alternates requested panel widths of 280dp and 380dp, subject to the same clamp.

Contents rows have a minimum height of 52dp. The expand action is independently 48dp square; the title takes the rest of the row and has vertical small spacing plus a trailing control inset. Nesting adds 14dp per level, visually capped at six indentation steps without limiting the data hierarchy.

The contents header uses 16dp leading, 12dp top, and 4dp trailing/bottom insets. Search uses 20dp horizontal and 12dp vertical sheet insets. Search results have 14dp vertical padding. The welcome area uses 28dp horizontal and 24dp vertical insets and centers its content vertically. Search opens expanded: a 90% display-height peek and an 85% display-height content view are requested by source; keyboard and small-window fit require device verification.

**The Reading Width Rule.** Keep the wide-window reader at least 320dp and preserve the full 48dp splitter interaction region.

## Elevation & Depth

Custom reader surfaces use flat color and a thin splitter rather than custom shadows. The drawer, bottom sheet, dialogs, popup menu, buttons and Snackbar inherit native Material/AppCompat depth and state treatments. The project defines no custom shadow or elevation scale. Do not replace native elevation with an invented CSS shadow vocabulary. There are no custom decorative animations; native controls retain platform motion behavior.

## Shapes

The document, contents rows and main reading regions are rectangular, edge-to-edge surfaces. Material buttons, outlined fields, dialogs and the search bottom sheet retain library shape defaults from `Theme.Material3.DayNight.NoActionBar` (Material Components 1.13.0); the app defines no custom radius tokens. Retain these inherited shapes instead of assigning a new global radius scale.

## Components

### Buttons

The welcome primary action is a filled native `MaterialButton`; recent books and indexing/cancel actions use `materialButtonOutlinedStyle`. Shapes, native state colors, typography and general content insets are inherited. The indexing button explicitly requests a 48dp height and full sheet width. Labels are Russian source strings; case behavior is inherited from the native style. No custom hover colors are declared.

Icon actions use custom path-based 24-unit vector drawings, on-surface color, a native borderless selectable background, 12dp insets and a 48dp square target. Their Russian content descriptions state the action. Do not substitute text glyphs or emoji for these paths.

### Contents navigation

The group expander and page label have separate actions. A group without a page toggles from its label; a page label opens its target. Leaf expanders are invisible and unfocusable while their reserved space keeps titles aligned. Current-page feedback combines primary-container fill, bold text and a current-section accessibility description. The accessibility label includes the hierarchy level; headings for contents, search and welcome are marked as such.

The contents header provides current-page reveal and a native overflow menu for collapse all, expand all and reveal. The toolbar provides contents, always-visible search/open actions and native overflow settings. On wide windows the contents action reveals the current item; on phones it opens the drawer.

### Search field and results

Search uses a `TextInputLayout` with outlined box mode and a single-line `TextInputEditText` with search IME action. Two native radio controls, each with a 48dp minimum height, choose title or full-text search. Title results and full-text hits use a bold result title and a supporting excerpt, limited to three lines. Focusable result rows use the native selectable background.

The status area distinguishes empty input, no matches, partial indexing, progress, unread pages and completion. The indexing action changes between create/continue, cancel and completed states, becoming disabled when a complete index has no skipped pages. Query changes debounce by 220ms; progress refreshes debounce by 400ms. These are scheduling delays, not animation tokens.

### Reader and history

The WebView uses its white background, original page styles, overview/wide viewport behavior, pinch zoom and page scrolling. In-page search uses native WebView find highlighting. Its label can choose a term from a multiword query; previous/next match and close actions retain 48dp targets.

The bottom strip contains back/forward history actions, a two-line ellipsized page label and home. Back/forward controls are disabled when unavailable and use 0.35 alpha. These are browser history actions, not adjacent table-of-contents topics.

Horizontal single-finger swipes navigate distinct HTML pages in TOC preorder. Left is next, right previous; folders/duplicate anchors are skipped, boundaries do not wrap. A page that can still pan in the gesture direction retains that scroll for the entire gesture. A fresh swipe at its edge may turn a page. Pinch, vertical gestures, text selection/long press and canceled sequences never turn pages. No page-turn animation overrides the original HTML.

The Updates screen inherits Material 3 surfaces, 64dp toolbar, 24dp content padding and system 22/16/14sp text. Separate native buttons check, download and install updates. A linear progress indicator and cancel action appear only during an explicit request. The standard Android installer and source-permission screen handle installation; the document WebView never performs it.

### Splitter

The focusable, clickable resize region exposes a description of drag/click behavior. Dragging starts after a 4dp movement threshold; clicking also changes panel width. Keep the outline line centered within the larger interaction region.

### Feedback and native overlays

Import progress uses an indeterminate linear indicator, supporting status text and a cancel action while copying. Errors, settings, external-link confirmation and information use Material dialogs. Short feedback uses a Snackbar allowing up to five text lines. Theme choices are system, light and dark; page colors are explained alongside the theme chooser.

Back closes the drawer first, then clears in-page highlighting, then traverses WebView history, then leaves the activity. Keep this order consistent with the visible layer. Native selectable backgrounds, enabled states and focus behavior are retained. Keyboard focus, TalkBack, large-font layout and native motion behavior have not been verified on a running device.

## Do's and Don'ts

### Do:

- **Do** preserve document HTML colors and the white WebView canvas in both app themes.
- **Do** use native Material 3 controls, system `sans` text and scalable `sp` sizes.
- **Do** keep expansion and page opening separate in the contents hierarchy.
- **Do** maintain 48dp icon actions and splitter region, with at least 320dp for the wide-window reader.
- **Do** show selection with both fill and bold text, and name actions for accessibility.
- **Do** distinguish a partial or incomplete search index from a completed one.

### Don't:

- **Don't** algorithmically invert document pages, technical images or tables for dark app chrome.
- **Don't** relabel history actions as previous/next contents topics.
- **Don't** replace vector action paths with text glyphs or emoji.
- **Don't** introduce custom fonts, radii, shadows or motion tokens without an implemented need.
- **Don't** treat the source-based layout record or sidecar previews as device screenshots or completed visual QA.
