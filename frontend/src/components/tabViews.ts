/** What `ViewTabs` and `SwipeTrack` share about the views they present. */
export interface TabView {
  id: string
  label: string
  /** SVG path data on a 24×24 grid, drawn as a stroke. */
  icon: string
  /** A count, a quiet dot, or a pulsing dot for something happening right now. */
  badge?: number | 'dot' | 'live'
}

/** Ids tying each tab to its panel for assistive technology. */
export const tabId = (viewId: string): string => `view-tab-${viewId}`
export const panelId = (viewId: string): string => `view-panel-${viewId}`

/** Hand-drawn 24×24 stroke icons: a notice board, a shopping bag and a compass. */
export const VIEW_ICONS = {
  board:
    'M5 4h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1zM8 9h8M8 13h8M8 17h5',
  shop: 'M6 8h12l1 12H5L6 8zM9 8V6a3 3 0 0 1 6 0v2',
  advisor: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM15.5 8.5l-2 5-5 2 2-5 5-2z',
} as const
