---
name: Tonal Folio
colors:
  surface: '#faf9fc'
  surface-dim: '#dbd9dd'
  surface-bright: '#faf9fc'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f5f3f7'
  surface-container: '#efedf1'
  surface-container-high: '#e9e7eb'
  surface-container-highest: '#e3e2e6'
  on-surface: '#1b1b1e'
  on-surface-variant: '#494551'
  inverse-surface: '#303033'
  inverse-on-surface: '#f2f0f4'
  outline: '#7a7582'
  outline-variant: '#cbc4d2'
  surface-tint: '#6750a4'
  primary: '#4f378a'
  on-primary: '#ffffff'
  primary-container: '#6750a4'
  on-primary-container: '#e0d2ff'
  inverse-primary: '#cfbcff'
  secondary: '#625b71'
  on-secondary: '#ffffff'
  secondary-container: '#e8def9'
  on-secondary-container: '#686177'
  tertiary: '#633b48'
  on-tertiary: '#ffffff'
  tertiary-container: '#7d5260'
  on-tertiary-container: '#ffcbda'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e9ddff'
  primary-fixed-dim: '#cfbcff'
  on-primary-fixed: '#22005d'
  on-primary-fixed-variant: '#4f378a'
  secondary-fixed: '#e8def9'
  secondary-fixed-dim: '#ccc2dc'
  on-secondary-fixed: '#1e192b'
  on-secondary-fixed-variant: '#4a4358'
  tertiary-fixed: '#ffd9e3'
  tertiary-fixed-dim: '#eeb8c8'
  on-tertiary-fixed: '#31111d'
  on-tertiary-fixed-variant: '#633b48'
  background: '#faf9fc'
  on-background: '#1b1b1e'
  surface-variant: '#e3e2e6'
typography:
  display-lg:
    fontFamily: Roboto Flex
    fontSize: 36px
    fontWeight: '400'
    lineHeight: 44px
    letterSpacing: -0.25px
  display-lg-mobile:
    fontFamily: Roboto Flex
    fontSize: 28px
    fontWeight: '400'
    lineHeight: 36px
    letterSpacing: 0px
  headline-lg:
    fontFamily: Roboto Flex
    fontSize: 32px
    fontWeight: '500'
    lineHeight: 40px
    letterSpacing: 0px
  headline-md:
    fontFamily: Roboto Flex
    fontSize: 24px
    fontWeight: '500'
    lineHeight: 32px
    letterSpacing: 0px
  headline-sm:
    fontFamily: Roboto Flex
    fontSize: 20px
    fontWeight: '500'
    lineHeight: 28px
    letterSpacing: 0px
  title-lg:
    fontFamily: Roboto Flex
    fontSize: 18px
    fontWeight: '500'
    lineHeight: 24px
    letterSpacing: 0.15px
  title-md:
    fontFamily: Roboto Flex
    fontSize: 16px
    fontWeight: '500'
    lineHeight: 24px
    letterSpacing: 0.15px
  body-lg:
    fontFamily: Noto Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0.5px
  body-md:
    fontFamily: Noto Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0.25px
  body-sm:
    fontFamily: Noto Sans
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.4px
  label-lg:
    fontFamily: Roboto Flex
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.1px
  label-md:
    fontFamily: Roboto Flex
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.5px
  label-sm:
    fontFamily: Roboto Flex
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.5px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1rem
  margin-tablet: 1.5rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system establishes a clean, spacious, and atmospheric Material Design 3 (Material You) aesthetic tailored for a modern Comic & eBook Reader on Android 13+ (API 33+). It targets avid readers and comic enthusiasts who value distraction-free reading, tactile library organization, and native platform familiarity.

The interface balances **Corporate / Modern** precision with **Minimalism**, using warm tonal surfaces, generous whitespace, and edge-to-edge layouts. The UI recedes behind the artwork and prose of the user's library, evoking calm focus, editorial refinement, and effortless navigation.

## Colors

The palette is rooted in Material You light mode with warm, paper-like tonal surfaces that reduce eye strain during extended reading sessions.

- **Surface & Background:** Primary canvas uses `#FDFBFF` (`surface`), paired with `#F3F0F7` (`surface-container`) for cards, navigation bars, and grouped content.
- **Primary (`#6750A4`):** Reserved for high-emphasis actions such as the primary Floating Action Button (FAB), active reading progress indicators, and primary call-to-action buttons.
- **Secondary (`#625B71`):** Used for tonal fills on filter chips, secondary action buttons, and active navigation indicator pills.
- **Tertiary (`#7D5260`):** Applied sparingly for accents such as "New" or "Unread" badges, bookmarks, and promotional highlights.
- **Contrast & Accessibility:** All text and interactive icons maintain a minimum 4.5:1 contrast ratio against their respective tonal containers. On-surface text uses deep neutral charcoal (`#1C1B1F`) rather than pure black to preserve warmth.

## Typography

The typographic hierarchy pairs **Roboto Flex** for expressive, crisp UI headers and navigation labels with **Noto Sans** for legible, multilingual body copy and metadata.

- **Reader View:** Within the eBook reader canvas, users may override the body font with serif or sans-serif preferences, while the chrome and HUD strictly adhere to the system scale.
- **Scaling:** `display-lg` is reserved for tablet/foldable hero headers; compact mobile viewports (`< 600dp`) automatically step down to `display-lg-mobile` (28px) to prevent awkward wrapping on book titles.
- **Tabular Figures:** Apply `font-variant-numeric: tabular-nums` to page numbers, reading percentages, and chapter counts.

## Layout & Spacing

The design system uses a **Fluid Grid** engineered for Android 13+ edge-to-edge layouts, respecting system status bar and gesture navigation insets.

- **Mobile (`< 600dp`):** 4-column fluid grid with `16px` (`1rem`) outer margins and `16px` (`1rem`) gutters. Library grids typically display 2 columns of comic/book covers.
- **Tablet & Foldables (`600dp - 840dp`):** 8-column fluid grid with `24px` (`1.5rem`) outer margins and `16px` gutters. Library grids expand to 3–4 columns, and navigation transitions from a bottom bar to a left-aligned Navigation Rail.
- **Expanded (`> 840dp`):** 12-column fluid grid with `24px` margins, supporting dual-pane reader/library views.
- **Edge-to-Edge:** Content scrolls behind translucent system bars; apply bottom padding equal to the navigation bar height plus `space-md` to the final item in scrollable lists.

## Elevation & Depth

Visual hierarchy relies on **Tonal Layers** rather than heavy drop shadows, creating a flat yet tactile sense of depth.

- **Surface Hierarchy:** Base canvas (`#FDFBFF`) sits at Level 0. Interactive cards, bottom sheets, and navigation bars step up to `surface-container` (`#F3F0F7`) at Level 1–2.
- **Overlays & FABs:** Floating Action Buttons and active modal dialogs may use a subtle, diffused ambient shadow (`0px 4px 12px rgba(28, 27, 31, 0.08)`) combined with a higher-tint tonal surface to separate them from scrolling book covers.
- **Scrims:** Modal drawers and reader menus use a `32%` opacity `#1C1B1F` scrim.

## Shapes

The shape language is generous and friendly, anchored by a `16px` (`1rem`) base corner radius for large containers and `8px` (`0.5rem`) for smaller elements.

- **Cards & Cover Containers:** Use `16px` (`rounded-lg`) to `28px` (`rounded-xl`) corners. Book and comic covers inside cards use `12px` corners to nest cleanly within their parent padding.
- **Pills:** Search bars, filter chips, and active navigation indicators use full pill rounding (`9999px`).
- **FABs:** Standard and extended Floating Action Buttons strictly use a `16px` corner radius (`rounded-lg`) per Material Design 3 specifications.

## Components

- **Buttons:** Primary actions use filled pill buttons (`#6750A4` background, `#FFFFFF` text, `40px` minimum height). Secondary actions use tonal buttons (`#E8DEF8` background, `#1D192B` text).
- **Chips:** Filter chips (e.g., "Comics", "eBooks", "Unread") are pill-shaped with `32px` height, `1px` `#79747E` outline when unselected, and `#E8DEF8` tonal fill with a leading checkmark when selected.
- **Search Bar:** A persistent, pill-shaped `surface-container` (`#F3F0F7`) bar at the top of the library with `56px` height, leading search icon, and trailing avatar/profile trigger.
- **Cards:** Library items use borderless `surface-container` cards (`16px` radius) or clean cover-first layouts with `12px` rounded artwork, a `title-md` title (max 2 lines), `body-sm` author/issue metadata, and a thin `4px` linear progress bar along the bottom edge.
- **Lists:** Settings and chapter lists use `56px`–`72px` row heights with `16px` horizontal padding, leading icons or cover thumbnails, and subtle `1px` `#E7E0EC` dividers only when grouping distinct sections.
- **Checkboxes & Radio Buttons:** Standard Material 3 `18px` targets with `48dp` touch areas, using `#6750A4` fill when active.
- **Input Fields:** Outlined or filled tonal text fields (`#F3F0F7`) with `16px` top corners (filled) or `12px` all-around (outlined) and floating `label-md` labels.
- **Reader HUD (Product-Specific):** Top and bottom reader toolbars float as translucent pill or `24px`-rounded `surface-container` overlays that auto-hide on tap, featuring a scrubber slider for page/chapter navigation and quick toggles for brightness and typography.